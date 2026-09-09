package com.linggong.utils;

import com.linggong.dto.Result;
import com.linggong.exception.RateLimiterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 接口限流（@RateLimiter 超限）：单独处理返回注解里的友好提示，
     * 不能被下方 RuntimeException 兜底吞成「服务器异常」。
     */
    @ExceptionHandler(RateLimiterException.class)
    public Result handleRateLimiterException(RateLimiterException e) {
        log.warn("限流拒绝: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /**
     * 运行时异常兜底（预期外的错误）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.fail("服务器异常");
    }

    /**
     * 参数校验失败（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(msg);
    }
}
