package com.bilibili.video.client.dto;

import lombok.Data;

/**
 * 内容审核结果
 */
@Data
public class ModerationResult {
    private Boolean isRisky;
    private String riskLevel;
    private String reason;
}
