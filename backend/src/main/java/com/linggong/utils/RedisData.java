package com.linggong.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缓存「逻辑过期」包装。
 *
 * <p>逻辑过期方案：缓存 value 不是裸数据，而是「逻辑过期时间 + 数据」。
 * key 物理上永不过期，读到时由应用判断 expireTime 是否已过，已过则后台异步重建。
 *
 * <p>相比互斥锁方案，好处是热点 key 过期时用户永远能拿到旧数据（可用性优先），
 * 重建开销不阻塞请求；代价是短时间内可能返回旧数据（最终一致性）。
 */
@Data
public class RedisData {

    /** 逻辑过期时间 */
    private LocalDateTime expireTime;

    /** 真实数据 */
    private Object data;
}
