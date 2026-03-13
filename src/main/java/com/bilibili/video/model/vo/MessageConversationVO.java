package com.bilibili.video.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageConversationVO {
    private Long targetId;
    private String targetName;
    private String targetAvatar;
    private String lastContent;
    private Integer unread;
    private LocalDateTime lastTime;
}
