package com.bilibili.video.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class MessageSummaryVO {
    private Long totalUnread;
    private Long messageUnread;
    private Long notificationUnread;
    private List<MessageVO> latestMessages;
    private List<NotificationVO> latestNotifications;
}
