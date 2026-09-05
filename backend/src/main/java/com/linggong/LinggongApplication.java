package com.linggong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 本地零工平台 —— 启动类
 */
@SpringBootApplication
@MapperScan("com.linggong.mapper")
public class LinggongApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinggongApplication.class, args);
    }
}
