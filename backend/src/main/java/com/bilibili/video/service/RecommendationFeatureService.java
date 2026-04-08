package com.bilibili.video.service;

import java.util.List;
import java.util.Map;

public interface RecommendationFeatureService {

    /**
     * 同步视频标签特征（默认置信度策略）。
     */
    void syncVideoTagFeatures(Long videoId, List<Long> tagIds, String source, String version);

    /**
     * 同步视频标签特征（可按标签设置置信度）。
     */
    void syncVideoTagFeatures(Long videoId, List<Long> tagIds, String source, String version, Map<Long, Double> confidenceByTagId);

    /**
     * 根据用户在某视频上的行为，增加用户兴趣标签权重。
     */
    void increaseUserInterestByVideo(Long userId, Long videoId, double delta);

    /**
     * 获取用户兴趣标签 TopN。
     */
    List<Long> listTopInterestTagIds(Long userId, int limit);
}

