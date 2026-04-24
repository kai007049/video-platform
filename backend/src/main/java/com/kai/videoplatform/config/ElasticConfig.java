package com.kai.videoplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@ConditionalOnProperty(prefix = "video.search", name = "enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackages = "com.kai.videoplatform.search")
public class ElasticConfig {
}