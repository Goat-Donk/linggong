package com.linggong.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 的分布式 ID 生成器（雪花算法简化版）。
 *
 * <p>结构：1 位符号位（恒 0）+ 31 位时间戳 + 32 位序列号，共 64 位。
 * <ul>
 *   <li>高 32 位：相对 BEGIN_TIMESTAMP 的秒数，31 位可支撑约 69 年（到 2091 年）。</li>
 *   <li>低 32 位：序列号，用 Redis INCR 按天自增，单日单业务可支撑 42 亿个 id。</li>
 * </ul>
 *
 * <p>用于报名记录（订单）id 生成，替代数据库自增，避免分库分表后主键冲突。
 * 教学版不做时钟回拨处理，生产环境可用雪花算法框架（如 Hutool Snowflake）或加回拨校验。
 */
@Component
public class RedisIdWorker {

    /** 起始时间戳（2022-01-01 00:00:00 UTC 的秒数） */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /** 序列号占用的位数 */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成一个分布式唯一 id。
     *
     * @param keyPrefix 业务前缀，用于区分不同业务的序列号（key = icr:{keyPrefix}:{yyyy:MM:dd}）
     * @return 64 位唯一 id
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳（相对起始时间的秒数）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号（按天自增，跨天自动从 1 重新开始）
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接：时间戳左移 32 位，低 32 位放序列号
        return timestamp << COUNT_BITS | count;
    }
}
