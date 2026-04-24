package com.kai.videoplatform.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
}