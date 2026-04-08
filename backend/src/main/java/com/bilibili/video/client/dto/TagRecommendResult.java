package com.bilibili.video.client.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签推荐结果
 */
@Data
public class TagRecommendResult {
    private List<String> tags;
    private String rationale;

    public static TagRecommendResult empty() {
        TagRecommendResult result = new TagRecommendResult();
        result.setTags(new ArrayList<>());
        result.setRationale("agent_service_unavailable");
        return result;
    }
}
