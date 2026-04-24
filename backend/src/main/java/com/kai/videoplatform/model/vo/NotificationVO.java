package com.kai.videoplatform.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {
    private Long id;
    private Long userId;
    private String type;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
}