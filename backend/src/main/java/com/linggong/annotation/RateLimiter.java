package com.linggong.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（滑动窗口）。
 *
 * <p>配合 {@code RateLimiterAspect} 使用：在 Controller 方法上加本注解，
 * 请求进入方法前会走 Redis 滑动窗口限流，超限直接抛 {@code RateLimiterException}，
 * 由全局异常处理器返回友好提示。
 *
 * <p>实现要点（对齐黑马点评加限流的常用做法）：
 * <ul>
 *   <li>窗口用 Redis ZSet 存请求时间戳，Lua 原子完成「删窗口外 → 统计 → 写入」，避免并发计数错误；</li>
 *   <li>{@code type} 决定限流维度：
 *       {@link LimitType#METHOD} 整个方法限流（防接口过载 / 爬虫），
 *       {@link LimitType#IP} 按客户端 IP（防短信轰炸等），
 *       {@link LimitType#USER} 按当前登录用户（防单用户刷接口）；</li>
 *   <li>{@code window}（秒）内最多允许 {@code limit} 次，超过返回 {@code message} 提示。</li>
 * </ul>
 *
 * <p>例：<pre>
 * {@code @RateLimiter(window = 60, limit = 10, type = LimitType.IP, message = "发送过于频繁，请稍后再试")}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 滑动窗口大小（秒）。
     */
    long window() default 10;

    /**
     * 窗口内允许的最大请求次数。
     */
    long limit() default 10;

    /**
     * 超限时返回给前端的提示语。
     */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * 限流维度。
     */
    LimitType type() default LimitType.METHOD;

    /**
     * 限流维度：方法（全局限流）/ IP / 用户。
     */
    enum LimitType {
        /** 整个方法维度：不限用户/IP，接口级全局限流（防过载、防爬虫） */
        METHOD,
        /** 客户端 IP 维度（防短信轰炸、防爆破） */
        IP,
        /** 当前登录用户维度（防单用户刷接口） */
        USER
    }
}
