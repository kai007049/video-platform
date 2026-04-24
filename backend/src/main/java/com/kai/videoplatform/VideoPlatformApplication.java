package com.kai.videoplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仿 Bilibili 视频平台启动类
 */
@SpringBootApplication
@MapperScan("com.kai.videoplatform.mapper")
@EnableScheduling
public class VideoPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoPlatformApplication.class, args);
    }
}