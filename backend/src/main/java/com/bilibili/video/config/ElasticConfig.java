package com.bilibili.video.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@ConditionalOnProperty(prefix = "video.search", name = "enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackages = "com.bilibili.video.search")
public class ElasticConfig {
}
