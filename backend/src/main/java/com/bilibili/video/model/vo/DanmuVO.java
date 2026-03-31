package com.bilibili.video.model.vo;

import lombok.Data;

/**
 * 弹幕展示 VO
 */
@Data
public class DanmuVO {
    private Long id;
    private Long videoId;
    private Long userId;
    private String username;
    private String content;
    private Integer timePoint;
    private String clientMessageId;
}
