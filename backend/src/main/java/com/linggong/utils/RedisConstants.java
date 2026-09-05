package com.linggong.utils;

/**
 * Redis 相关常量：key 前缀 + 过期时间。
 *
 * <p>统一在这里维护 key 命名和 TTL，避免魔法值散落在业务代码里。
 */
public final class RedisConstants {

    /** 登录验证码 key 前缀，完整 key = login:code:{phone} */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 验证码有效期（分钟） */
    public static final Long LOGIN_CODE_TTL = 2L;

    /** 登录 token key 前缀，完整 key = login:token:{token}，value 存 UserDTO 的 JSON */
    public static final String LOGIN_USER_KEY = "login:token:";
    /** token 有效期（分钟） */
    public static final Long LOGIN_USER_TTL = 30L;

    private RedisConstants() {
    }
}
