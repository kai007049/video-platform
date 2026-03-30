package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.entity.Message;
import com.bilibili.video.entity.User;
import com.bilibili.video.mapper.MessageMapper;
import com.bilibili.video.model.dto.SendMessageDTO;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.vo.MessageConversationVO;
import com.bilibili.video.model.vo.MessageVO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private static final String UNREAD_KEY = "msg:unread:";

    private final MessageMapper messageMapper;
    private final MQService mqService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.bilibili.video.mapper.UserMapper userMapper;

    @Override
    public void sendMessage(Long senderId, SendMessageDTO dto) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(dto.getReceiverId());
        message.setContent(dto.getContent());
        message.setStatus(0);
        message.setSenderDeleted(0);
        message.setReceiverDeleted(0);
        messageMapper.insert(message);

        redisTemplate.opsForValue().increment(UNREAD_KEY + dto.getReceiverId(), 1);

        mqService.sendMessageNotify(new MessageNotifyMessage(dto.getReceiverId(), "message", message.getId(), dto.getContent()));
    }

    @Override
    public IPage<MessageVO> listMessages(Long userId, Long targetId, int page, int size) {
        Page<Message> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getSenderId, userId)
                        .eq(Message::getReceiverId, targetId)
                        .eq(Message::getSenderDeleted, 0)
                        .or()
                        .eq(Message::getSenderId, targetId)
                        .eq(Message::getReceiverId, userId)
                        .eq(Message::getReceiverDeleted, 0))
                .orderByAsc(Message::getCreateTime);
        IPage<Message> result = messageMapper.selectPage(pageParam, qw);
        return result.convert(this::toVO);
    }

    @Override
    public void markRead(Long userId, Long targetId) {
        Message update = new Message();
        update.setStatus(1);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getReceiverId, userId)
                .eq(Message::getSenderId, targetId)
                .eq(Message::getStatus, 0)
                .eq(Message::getReceiverDeleted, 0);

        messageMapper.update(update, wrapper);
        redisTemplate.delete(UNREAD_KEY + userId);
    }

    @Override
    public Long getUnreadCount(Long userId) {
        Object val = redisTemplate.opsForValue().get(UNREAD_KEY + userId);
        if (val instanceof Number) return ((Number) val).longValue();
        Long cnt = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getStatus, 0)
                .eq(Message::getReceiverDeleted, 0));
        redisTemplate.opsForValue().set(UNREAD_KEY + userId, cnt);
        return cnt;
    }

    @Override
    public java.util.List<MessageConversationVO> listConversations(Long userId) {
        java.util.List<Message> all = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getSenderDeleted, 0)
                        .or()
                        .eq(Message::getReceiverId, userId).eq(Message::getReceiverDeleted, 0))
                .orderByDesc(Message::getCreateTime));
        java.util.Map<Long, Message> latestMap = new java.util.LinkedHashMap<>();
        for (Message m : all) {
            Long targetId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            latestMap.putIfAbsent(targetId, m);
        }
        java.util.Map<Long, Long> unreadMap = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getStatus, 0)
                .eq(Message::getReceiverDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(Message::getSenderId, java.util.stream.Collectors.counting()));
        Map<Long, User> userMap = latestMap.isEmpty()
                ? new HashMap<>()
                : userMapper.selectBatchIds(latestMap.keySet())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return latestMap.entrySet().stream().map(e -> {
            Message m = e.getValue();
            MessageConversationVO vo = new MessageConversationVO();
            vo.setTargetId(e.getKey());
            com.bilibili.video.entity.User target = userMap.get(e.getKey());
            if (target != null) {
                vo.setTargetName(target.getUsername());
                vo.setTargetAvatar(target.getAvatar());
            }
            vo.setLastContent(m.getContent());
            vo.setUnread(unreadMap.getOrDefault(e.getKey(), 0L).intValue());
            vo.setLastTime(m.getCreateTime());
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void revokeMessage(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || !message.getSenderId().equals(userId)) {
            return;
        }
        message.setContent("[已撤回]");
        messageMapper.updateById(message);
    }

    @Override
    public void clearConversation(Long userId, Long targetId) {
        Message update = new Message();
        update.setSenderDeleted(1);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSenderId, userId)
                .eq(Message::getReceiverId, targetId)
                .eq(Message::getSenderDeleted, 0);

        messageMapper.update(update, wrapper);

        Message updateReceive = new Message();
        updateReceive.setReceiverDeleted(1);

        LambdaQueryWrapper<Message> wrapperReceive = new LambdaQueryWrapper<>();
        wrapperReceive.eq(Message::getSenderId, targetId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getReceiverDeleted, 0);

        messageMapper.update(updateReceive, wrapperReceive);

        LambdaQueryWrapper<Message> deleteWrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderDeleted, 1)
                .eq(Message::getReceiverDeleted, 1)
                .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getReceiverId, targetId)
                        .or()
                        .eq(Message::getSenderId, targetId).eq(Message::getReceiverId, userId));
        messageMapper.delete(deleteWrapper);

        redisTemplate.delete(UNREAD_KEY + userId);
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
