package com.bilibili.video.service.impl;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Schema(description = "缓存的推荐元数据")
public class CachedRecommendationMeta {

    private Double score;
    private List<String> channels = Collections.emptyList();
    private Integer rank;

    public CachedRecommendationMeta() {
    }
}
