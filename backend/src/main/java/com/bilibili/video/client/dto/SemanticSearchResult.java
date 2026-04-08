package com.bilibili.video.client.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 语义搜索结果
 */
@Data
public class SemanticSearchResult {
    private List<Long> videoIds;
    private List<Double> scores;

    public static SemanticSearchResult empty() {
        SemanticSearchResult result = new SemanticSearchResult();
        result.setVideoIds(new ArrayList<>());
        result.setScores(new ArrayList<>());
        return result;
    }
}
