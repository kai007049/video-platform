package com.bilibili.video.model.dto;

import lombok.Data;

@Data
public class SendMessageDTO {
    private Long receiverId;
    private String content;
}
