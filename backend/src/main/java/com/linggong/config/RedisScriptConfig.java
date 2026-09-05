package com.linggong.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Lua 脚本配置：预加载秒杀脚本为 Bean，避免每次请求重复解析脚本。
 */
@Configuration
public class RedisScriptConfig {

    /** 秒杀脚本：静态加载一次，所有请求共享 */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> seckillScript() {
        return SECKILL_SCRIPT;
    }
}
