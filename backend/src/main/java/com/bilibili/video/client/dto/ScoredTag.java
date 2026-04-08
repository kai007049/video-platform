package com.bilibili.video.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScoredTag {
    @JsonProperty("tag")
    private String tag;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("reason")
    private String reason;
}

