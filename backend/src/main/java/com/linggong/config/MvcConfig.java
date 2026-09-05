package com.linggong.config;

import com.linggong.interceptor.LoginInterceptor;
import com.linggong.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * MVC 配置：注册登录相关的两个拦截器 + 静态资源映射（上传图片）。
 *
 * <p>执行顺序：RefreshTokenInterceptor（order 0，先跑，负责解析用户）
 * → LoginInterceptor（order 1，后跑，负责校验是否登录）。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    /** 本地上传目录（与 application.yml 的 linggong.upload.dir 一致，末尾带 /） */
    @Value("${linggong.upload.dir}")
    private String uploadDir;

    public MvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 刷新 token 拦截器：放行所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);

        // 2. 登录校验拦截器：排除无需登录的写接口（发验证码、登录）
        //    接口文档等浏览类 GET 的放行在 LoginInterceptor 内用前缀判断处理。
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login"
                ).order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 /uploads/** 映射到本地上传目录，上传的图片通过 URL 直接访问。
        // 用 Paths.toUri() 转成规范的 file:/// URI，避免 Windows 下反斜杠路径解析失败。
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(Paths.get(uploadDir).toUri().toString());
    }
}
