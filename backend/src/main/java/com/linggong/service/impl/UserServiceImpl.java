package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.LoginFormDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.User;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IUserService;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.RegexUtils;
import com.linggong.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现。
 *
 * <p>约定：Service 直接返回 {@link Result}（与黑马点评保持一致），
 * 业务失败（验证码错误等）用 {@code Result.fail} 表达，而非抛异常，简单直观。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /** 签到 key 的月份后缀格式：:yyyyMM（拼接成 sign:{userId}:{yyyyMM}） */
    private static final DateTimeFormatter SIGN_MONTH_FORMATTER = DateTimeFormatter.ofPattern(":yyyyMM");

    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 存 Redis，2 分钟过期
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 4. 模拟发送短信（真实项目接短信服务商，这里打日志方便联调）
        log.info("发送登录验证码：phone={}, code={}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        // 1. 校验手机号（DTO 已校验，这里再兜底一次）
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }
        // 3. 按手机号查用户，不存在则自动注册
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 4. 生成 token（无横线 UUID），把 UserDTO 存 Redis Hash，TTL 加随机值防雪崩
        String token = IdUtil.simpleUUID();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, beanToHash(userDTO));
        Long random = (long) RandomUtil.randomInt(3, 10);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL + random, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        // 删除 Redis 中的 token；ThreadLocal 的清理由拦截器 afterCompletion 统一负责
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @Override
    public void refreshUserCache(String token, Long userId) {
        if (token == null || token.isBlank() || userId == null) {
            return;
        }
        // 重新查库拿最新昵称/头像，回写 Redis 的 token 缓存 + ThreadLocal
        User user = getById(userId);
        if (user == null) {
            return;
        }
        UserDTO fresh = BeanUtil.copyProperties(user, UserDTO.class);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, beanToHash(fresh));
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        UserHolder.saveUser(fresh);
    }

    @Override
    public Result sign() {
        // 1. 当前用户 + 当前日期
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = signKey(userId, now);
        // 2. 今天是本月的第几天，offset = 天数 - 1（bitmap 从 0 开始）
        int dayOfMonth = now.getDayOfMonth();
        // 3. SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1L, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = signKey(userId, now);
        int dayOfMonth = now.getDayOfMonth();
        // BITFIELD key GET u{dayOfMonth} 0：取本月 1 号到今天的所有签到位（大端，今天落在最低位）
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 从最低位（今天）往前数连续 1 的个数
        int count = 0;
        while ((num & 1) != 0) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 拼接签到 key：sign:{userId}:{yyyyMM}。
     */
    private String signKey(Long userId, LocalDateTime now) {
        return RedisConstants.USER_SIGN_KEY + userId + now.format(SIGN_MONTH_FORMATTER);
    }

    /**
     * UserDTO 转 Redis Hash（对齐黑马点评原版：token 的 value 存 Hash 而非 JSON 字符串）。
     *
     * <p>Redis Hash 只能存字符串，所以把所有字段值统一 toString；null 字段转空串，
     * 读取时由 {@code BeanUtil.fillBeanWithMap} 再按字段类型转回（Long/Integer 等）。
     */
    private Map<String, String> beanToHash(UserDTO userDTO) {
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, String> stringMap = new HashMap<>();
        userMap.forEach((k, v) -> stringMap.put(k, v != null ? v.toString() : ""));
        return stringMap;
    }

    /**
     * 首次登录自动注册：创建默认用户（昵称 = 用户 + 手机尾号 4 位）。
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户" + phone.substring(phone.length() - 4));
        user.setRole(0); // 默认打工人
        save(user);
        return user;
    }
}
