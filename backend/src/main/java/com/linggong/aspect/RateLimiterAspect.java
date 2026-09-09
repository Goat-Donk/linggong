package com.linggong.aspect;

import cn.hutool.core.util.StrUtil;
import com.linggong.annotation.RateLimiter;
import com.linggong.exception.RateLimiterException;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link RateLimiter} 注解限流切面：请求进入标注了 {@code @RateLimiter} 的 Controller 方法前，
 * 用 Redis ZSet 滑动窗口判断是否超限，超限抛 {@link RateLimiterException}。
 *
 * <p>限流维度（注解 {@code type}）决定 key 后缀：
 * <ul>
 *   <li>{@code METHOD}：整个方法维度，key = rate:limit:{全类名.方法名}（接口级全局限流，防过载/爬虫）；</li>
 *   <li>{@code USER}：按当前登录用户，key 再拼 userId（防单用户刷接口；未登录时降级为 IP，避免公开接口 NPE）；</li>
 *   <li>{@code IP}：按客户端 IP，key 再拼 IP（防短信轰炸等）。</li>
 * </ul>
 *
 * <p>原子性：删除窗口外记录 + 统计 + 写入 + 设过期都在 {@code rate_limiter.lua} 内一次 Redis 执行完成，
 * 高并发下不会数错。
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    /** 限流 Lua 脚本：滑动窗口判断 + 写入（原子） */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("lua/rate_limiter.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiterAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(rateLimiter)")
    public Object aroundRateLimit(ProceedingJoinPoint pjp, RateLimiter rateLimiter) throws Throwable {
        String key = buildKey(pjp, rateLimiter);

        // 本次请求唯一标识：时间戳-随机数，避免同一毫秒多条请求的 ZSet member 互相覆盖
        long now = System.currentTimeMillis();
        String member = now + "-" + ThreadLocalRandom.current().nextInt(100000, 1000000);

        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimiter.window() * 1000L), // window 秒 → 毫秒
                String.valueOf(rateLimiter.limit()),
                String.valueOf(now),
                member);

        if (result != null && result == 0L) {
            log.warn("接口限流触发：key={}, window={}s, limit={}", key, rateLimiter.window(), rateLimiter.limit());
            throw new RateLimiterException(rateLimiter.message());
        }

        return pjp.proceed();
    }

    /**
     * 构造限流 key：rate:limit:{全类名.方法名}[:userId|:ip]
     */
    private String buildKey(ProceedingJoinPoint pjp, RateLimiter rateLimiter) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        StringBuilder sb = new StringBuilder(RedisConstants.RATE_LIMIT_KEY)
                .append(signature.getDeclaringTypeName())
                .append('.')
                .append(signature.getName());

        switch (rateLimiter.type()) {
            case USER:
                Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
                // 未登录降级为 IP（公开接口挂了 USER 维度也能工作）
                sb.append(':').append(userId != null ? userId : getIp());
                break;
            case IP:
                sb.append(':').append(getIp());
                break;
            case METHOD:
            default:
                // 方法级全局限流，key 无维度后缀
                break;
        }
        return sb.toString();
    }

    /**
     * 取客户端 IP：优先 X-Forwarded-For 首段（经 nginx 反代时真实 IP 在此），否则 RemoteAddr。
     */
    private String getIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwarded)) {
            // 多个代理时形如 "client, proxy1, proxy2"，取第一个（客户端真实 IP）
            int comma = forwarded.indexOf(',');
            String ip = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            if (StrUtil.isNotBlank(ip)) {
                return ip;
            }
        }
        String remote = request.getRemoteAddr();
        return StrUtil.isBlank(remote) ? "unknown" : remote;
    }
}
