package com.linggong.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.linggong.dto.UserDTO;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新 token 拦截器：放行所有请求，不做登录校验。
 *
 * <p>职责：
 * <ol>
 *   <li>从 header 拿 token，去 Redis 查对应用户，写入 {@link UserHolder}；</li>
 *   <li>刷新 token 有效期（实现"活跃用户永不过期"的滑动过期）。</li>
 * </ol>
 * 是否登录由后续的 {@link LoginInterceptor} 判断。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("authorization");
        if (token == null || token.isBlank()) {
            return true; // 没带 token，放行
        }

        // 查 Redis 拿用户（Hash 存储，对齐黑马点评原版）
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return true; // token 不存在/已过期，放行
        }

        // 解析并写入 ThreadLocal（fillBeanWithMap 按字段类型把 String 转回 Long/Integer）
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        // 刷新 token 有效期
        stringRedisTemplate.expire(
                RedisConstants.LOGIN_USER_KEY + token,
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );
        return true;
    }
}
