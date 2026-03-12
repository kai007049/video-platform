package com.bilibili.video.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.model.vo.NotificationVO;

public interface NotificationService {

    void sendNotification(Long userId, String type, String content);

    IPage<NotificationVO> listNotifications(Long userId, int page, int size);

    void markRead(Long userId, Long notificationId);

    Long getUnreadCount(Long userId);
}
