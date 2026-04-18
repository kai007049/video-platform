package com.bilibili.video.service.impl;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class CachedRecommendationMeta {

    private Double score;
    private List<String> channels = Collections.emptyList();
    private Integer rank;

    public CachedRecommendationMeta() {
    }
}
