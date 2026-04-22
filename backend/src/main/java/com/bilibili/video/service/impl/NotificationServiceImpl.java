package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.entity.Notification;
import com.bilibili.video.mapper.NotificationMapper;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.vo.NotificationVO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final String UNREAD_KEY = "notify:unread:";

    private final NotificationMapper notificationMapper;
    private final MQService mqService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void sendNotification(Long userId, String type, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setContent(content);
        n.setStatus(0);
        notificationMapper.insert(n);

        redisTemplate.opsForValue().increment(UNREAD_KEY + userId, 1);

        MessageNotifyMessage message = new MessageNotifyMessage(userId, "notification", n.getId(), content);
        message.setBizKey("message:user:" + userId + ":notification:" + n.getId());
        mqService.sendMessageNotify(message);
    }

    @Override
    public IPage<NotificationVO> listNotifications(Long userId, int page, int size) {
        Page<Notification> pageParam = new Page<>(page, size);
        IPage<Notification> result = notificationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
        return result.convert(this::toVO);
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !n.getUserId().equals(userId)) return;
        n.setStatus(1);
        notificationMapper.updateById(n);
        redisTemplate.delete(UNREAD_KEY + userId);
    }

    @Override
    public Long getUnreadCount(Long userId) {
        Object val = redisTemplate.opsForValue().get(UNREAD_KEY + userId);
        if (val instanceof Number) return ((Number) val).longValue();
        Long cnt = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getStatus, 0));
        redisTemplate.opsForValue().set(UNREAD_KEY + userId, cnt);
        return cnt;
    }

    private NotificationVO toVO(Notification n) {
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
