package com.kai.videoplatform.model.dto;

import lombok.Data;

/**
 * 弹幕消息 DTO（WebSocket 收发）
 */
@Data
public class DanmuDTO {

    private Long id;
    private Long videoId;
    private Long userId;
    private String username;
    private String content;
    private Integer timePoint;
    private String clientMessageId;
}