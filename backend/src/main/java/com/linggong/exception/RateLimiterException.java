package com.linggong.exception;

/**
 * 接口限流异常：请求超过 {@code @RateLimiter} 阈值时由切面抛出。
 *
 * <p>在 {@code WebExceptionAdvice} 中单独处理返回友好提示，
 * 否则会被兜底的 RuntimeException handler 吞成「服务器异常」。
 */
public class RateLimiterException extends RuntimeException {

    public RateLimiterException(String message) {
        super(message);
    }
}
