package com.linggong.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁（教学版，对齐黑马点评）。
 *
 * <p>实现要点：
 * <ol>
 *   <li>加锁用 {@code SET key value NX EX timeout}（setIfAbsent），保证「只有一个线程抢到锁」；</li>
 *   <li>value 存「JVM 标识 + 线程 id」，用于释放时判断「锁是不是自己的」；</li>
 *   <li>释放时先比较 value 再删除，避免 A 线程的锁过期后被 B 线程拿到、A 却把 B 的锁误删。</li>
 * </ol>
 *
 * <p><b>分布式锁演化线</b>：
 * 单机 {@code synchronized} → 本类（SET NX EX 分布式锁）→ 修复误删锁（比较线程标识）→
 * 仍存在的隐患是「比较 + 删除」不是原子的（比较后、删除前锁可能过期被别人拿到），
 * 生产环境应改用 Redisson 的 {@code RLock}（内部用 Lua 保证「判断 + 释放」原子），或自己写 Lua 脚本原子释放。
 */
public class SimpleRedisLock implements ILock {

    /** 锁 key 统一前缀，最终 key = lock:{name} */
    private static final String KEY_PREFIX = "lock:";

    /** JVM 级标识（每次启动随机），配合线程 id 唯一标识「这把锁是谁上的」，防止跨实例误删锁 */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /** 业务锁名（如 "order:1"） */
    private final String name;

    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // value = JVM标识-线程id，唯一标识「这把锁是谁上的」
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 只有锁是自己上的才删除，避免误删别人的锁
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
