package com.linggong.config;

import com.linggong.interceptor.LoginInterceptor;
import com.linggong.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册登录相关的两个拦截器。
 *
 * <p>执行顺序：RefreshTokenInterceptor（order 0，先跑，负责解析用户）
 * → LoginInterceptor（order 1，后跑，负责校验是否登录）。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    public MvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 刷新 token 拦截器：放行所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);

        // 2. 登录校验拦截器：排除无需登录的路径（发验证码、登录）
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login"
                ).order(1);
    }
}
