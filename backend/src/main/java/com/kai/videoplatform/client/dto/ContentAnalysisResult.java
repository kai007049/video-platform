package com.kai.videoplatform.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 内容分析结果
 */
@Data
public class ContentAnalysisResult {
    @JsonProperty("suggested_tags")
    private List<String> suggestedTags;

    @JsonProperty("tag_scores")
    private List<ScoredTag> tagScores;

    @JsonProperty("suggested_category_id")
    private Integer suggestedCategoryId;

    @JsonProperty("suggested_category_name")
    private String suggestedCategoryName;

    @JsonProperty("generated_title")
    private String generatedTitle;

    @JsonProperty("summary")
    private String summary;
}
