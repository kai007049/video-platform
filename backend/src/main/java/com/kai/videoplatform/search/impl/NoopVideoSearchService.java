package com.kai.videoplatform.search.impl;

import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.search.VideoSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "video.search", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopVideoSearchService implements VideoSearchService {

    @Override
    public void index(Video video) {
        log.debug("Search disabled. Skip indexing video {}", video == null ? null : video.getId());
    }

    @Override
    public void delete(Long videoId) {
        log.debug("Search disabled. Skip deleting video {}", videoId);
    }
}