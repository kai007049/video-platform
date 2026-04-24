package com.kai.videoplatform.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class MessageSummaryVO {
    private Long totalUnread;
    private Long messageUnread;
    private Long notificationUnread;
    private Long systemUnread;
    private List<MessageVO> latestMessages;
    private List<NotificationVO> latestNotifications;
}