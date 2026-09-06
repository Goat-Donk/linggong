package com.linggong.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁（教学版，对齐黑马点评原版）。
 *
 * <p>实现要点：
 * <ol>
 *   <li>加锁用 {@code SET key value NX EX timeout}（setIfAbsent），保证「只有一个线程抢到锁」；</li>
 *   <li>value 存「JVM 标识 + 线程 id」，用于释放时判断「锁是不是自己的」；</li>
 *   <li>释放用 {@code unlock.lua} 脚本，把「比较标识 + 删除」合并成一步原子操作，
 *       避免 A 线程比较后、删除前锁过期被 B 拿到、A 误删 B 的锁。</li>
 * </ol>
 *
 * <p><b>分布式锁演化线</b>：
 * 单机 {@code synchronized} → 本类（SET NX EX 分布式锁）→ 修复误删锁（比较线程标识）
 * → Lua 原子释放（本类最终版，见 {@link #unlock()}）→ 生产环境用 Redisson 的 {@code RLock}
 * （内部同样是 Lua 保证原子，另附带可重入 / 自动续期等能力）。
 */
public class SimpleRedisLock implements ILock {

    /** 锁 key 统一前缀，最终 key = lock:{name} */
    private static final String KEY_PREFIX = "lock:";

    /** JVM 级标识（每次启动随机），配合线程 id 唯一标识「这把锁是谁上的」，防止跨实例误删锁 */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /** 释放锁的 Lua 脚本：只有锁是自己的才删除，且「判断 + 删除」原子 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
        // 用 Lua 脚本原子释放：只有「当前线程标识 == 锁中的值」才删除，
        // 消除非原子的 get→compare→delete 竞态（比较后删除前锁可能已易主）
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
