package com.bilibili.video.service.impl;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class CachedRecommendationWindow {

    private List<Long> ids = Collections.emptyList();
    private Integer windowSize;
    private Boolean hasMore;
    /** Unix 时间戳（秒） */
    private Long generatedAt;
    private Map<String, CachedRecommendationMeta> meta = Collections.emptyMap();

    public CachedRecommendationWindow() {
    }

    public CachedRecommendationWindow(List<Long> ids,
                                      Integer windowSize,
                                      Boolean hasMore,
                                      Long generatedAt,
                                      Map<String, CachedRecommendationMeta> meta) {
        this.ids = ids;
        this.windowSize = windowSize;
        this.hasMore = hasMore;
        this.generatedAt = generatedAt;
        this.meta = meta;
    }
}
