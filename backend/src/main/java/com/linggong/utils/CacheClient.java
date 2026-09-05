package com.linggong.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 统一缓存客户端，封装缓存的三大经典问题：
 * <ul>
 *   <li>缓存穿透 —— 空对象（查不到也缓存一个 ""，短 TTL）</li>
 *   <li>缓存击穿 —— 互斥锁（SET NX EX，抢到锁的线程重建缓存）</li>
 *   <li>缓存雪崩 —— 写缓存时 TTL 加随机值</li>
 * </ul>
 *
 * <p>设计对齐黑马点评：用泛型 + 函数式接口 {@link Function} 把「查数据库」的回调传入，
 * 让缓存读写逻辑与具体业务解耦。JSON 序列化用 hutool JSONUtil。
 */
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写入缓存，TTL 加 1~10 随机值，避免大量 key 同时过期（缓存雪崩）。
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(
                key, JSONUtil.toJsonStr(value), time + RandomUtil.randomLong(1, 10), unit);
    }

    /**
     * 删除缓存（更新/下架数据后调用，保证下次查询读到最新数据，避免缓存一致性旧数据）。
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存穿透（空对象）查询，不防击穿。
     *
     * @param keyPrefix  缓存 key 前缀
     * @param id         业务 id
     * @param type       返回类型
     * @param dbFallback 查数据库的回调
     * @param time       缓存有效期
     * @param unit       时间单位
     * @return 查询结果，不存在返回 null
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 命中（有值）
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空对象（""）：缓存的「不存在」标记
        if (json != null) {
            return null;
        }
        // 2. 查数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 写空对象，TTL 短一些，防止同一不存在 id 反复打库
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 3. 写缓存（带随机 TTL）
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 缓存穿透（空对象）+ 缓存击穿（互斥锁）查询。
     *
     * <p>相比 {@link #queryWithPassThrough}，多了互斥锁：缓存失效的瞬间多个请求并发打进来，
     * 只有抢到锁的线程去查库重建，其余线程等待后重试，避免同一时刻大量请求打穿缓存。
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                    Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        // 2. 加互斥锁，防击穿
        String lockKey = RedisConstants.LOCK_KEY_PREFIX + key;
        if (!tryLock(lockKey)) {
            // 没抢到锁：稍等后重试（注意此时不释放锁——锁不是自己的）
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待缓存互斥锁被中断", e);
            }
            return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
        }
        // 抢到锁，以下步骤一定在 finally 里释放锁
        try {
            // 3. 双重检查（可能别的线程已重建缓存）
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }
            // 4. 查数据库
            R r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 5. 写缓存
            this.set(key, r, time, unit);
            return r;
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 抢互斥锁：SET NX EX 原子操作，只有一个线程能抢到。
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放互斥锁。
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
