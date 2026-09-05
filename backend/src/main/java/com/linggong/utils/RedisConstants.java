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

    /** 岗位详情缓存 key 前缀，完整 key = cache:job:{id} */
    public static final String CACHE_JOB_KEY = "cache:job:";
    /** 岗位缓存有效期（分钟） */
    public static final Long CACHE_JOB_TTL = 30L;
    /** 空对象缓存有效期（分钟，比正常缓存短，避免「不存在」标记长期占用） */
    public static final Long CACHE_NULL_TTL = 2L;
    /** 缓存互斥锁 key 前缀，完整锁 key = lock:{缓存 key} */
    public static final String LOCK_KEY_PREFIX = "lock:";
    /** 互斥锁有效期（秒） */
    public static final Long LOCK_TTL = 10L;

    /** 岗位 GEO key 前缀，完整 key = geo:job:{categoryId}，member = 岗位 id，坐标 = (x, y) */
    public static final String GEO_JOB_KEY = "geo:job:";

    /** 报名剩余名额 key 前缀，完整 key = apply:stock:{jobId}，value 为剩余名额数字（发布岗位时预热） */
    public static final String APPLY_STOCK_KEY = "apply:stock:";
    /** 报名一人一单标记 key 前缀，完整 key = apply:order:{jobId}（Set，存已报名 workerId） */
    public static final String APPLY_ORDER_KEY = "apply:order:";
    /** 报名记录 id 的 RedisIdWorker 前缀，完整 key = icr:apply:{yyyy:MM:dd} */
    public static final String APPLY_ID_PREFIX = "apply";

    /** 关注集合 key 前缀，完整 key = follows:{userId}（Set，存被关注用户 id） */
    public static final String FOLLOWS_KEY = "follows:";

    private RedisConstants() {
    }
}
