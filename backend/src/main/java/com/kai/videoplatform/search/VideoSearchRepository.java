package com.kai.videoplatform.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface VideoSearchRepository extends ElasticsearchRepository<VideoDocument, Long> {
}