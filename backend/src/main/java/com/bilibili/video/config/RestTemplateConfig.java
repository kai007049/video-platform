package com.bilibili.video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 * 用于调用外部服务（如 agent-service）
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 连接超时 5 秒
        factory.setReadTimeout(30000);   // 读取超时 30 秒
        return new RestTemplate(factory);
    }
}
