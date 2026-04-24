package com.kai.videoplatform.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpProfileVO {
    private Long id;
    private String username;
    private String avatar;
    private LocalDateTime createTime;
    private long videoCount;
    private long fanCount;
    private long followingCount;
    private Boolean followed;  // 当前用户是否已关注
}