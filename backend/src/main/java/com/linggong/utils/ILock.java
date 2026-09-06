package com.linggong.utils;

/**
 * 分布式锁接口（对齐黑马点评教学版）。
 *
 * <p>定义加锁 / 释放锁两个能力。教学版实现见 {@link SimpleRedisLock}，
 * 生产环境推荐直接使用 Redisson 的 {@code RLock}（本项目已在 {@code RedissonConfig} 配置好）。
 */
public interface ILock {

    /**
     * 尝试获取锁。
     *
     * @param timeoutSec 锁的过期时间（秒），防止持锁线程崩溃后死锁
     * @return true 表示获取锁成功，false 表示获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁。
     */
    void unlock();
}
