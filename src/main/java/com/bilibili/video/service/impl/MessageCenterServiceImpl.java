package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.Message;
import com.bilibili.video.entity.Notification;
import com.bilibili.video.mapper.MessageMapper;
import com.bilibili.video.mapper.NotificationMapper;
import com.bilibili.video.model.vo.MessageSummaryVO;
import com.bilibili.video.model.vo.MessageVO;
import com.bilibili.video.model.vo.NotificationVO;
import com.bilibili.video.service.MessageCenterService;
import com.bilibili.video.service.MessageService;
import com.bilibili.video.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageCenterServiceImpl implements MessageCenterService {

    private final MessageService messageService;
    private final NotificationService notificationService;
    private final MessageMapper messageMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public MessageSummaryVO summary(Long userId) {
        MessageSummaryVO vo = new MessageSummaryVO();
        Long msgUnread = messageService.getUnreadCount(userId);
        Long notifyUnread = notificationService.getUnreadCount(userId);
        vo.setMessageUnread(msgUnread);
        vo.setNotificationUnread(notifyUnread);
        vo.setTotalUnread(msgUnread + notifyUnread);

        List<Message> latestMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .orderByDesc(Message::getCreateTime)
                .last("limit 5"));
        List<Notification> latestNotifications = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .last("limit 5"));

        vo.setLatestMessages(latestMessages.stream().map(this::toMessageVO).collect(Collectors.toList()));
        vo.setLatestNotifications(latestNotifications.stream().map(this::toNotificationVO).collect(Collectors.toList()));
        return vo;
    }

    private MessageVO toMessageVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setSenderId(m.getSenderId());
        vo.setReceiverId(m.getReceiverId());
        vo.setContent(m.getContent());
        vo.setStatus(m.getStatus());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    private NotificationVO toNotificationVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setType(n.getType());
        vo.setContent(n.getContent());
        vo.setStatus(n.getStatus());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
