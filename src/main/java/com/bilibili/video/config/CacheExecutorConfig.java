package com.bilibili.video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CacheExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService cacheDelayExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
