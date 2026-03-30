package com.bilibili.video.model.vo;

import lombok.Data;

@Data
public class CreatorStatsVO {
    private long totalPlayCount;
    private long totalLikeCount;
    private long videoCount;
    private long fanCount;
}
