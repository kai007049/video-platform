package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.entity.Message;
import com.bilibili.video.mapper.MessageMapper;
import com.bilibili.video.model.dto.SendMessageDTO;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.vo.MessageVO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private static final String UNREAD_KEY = "msg:unread:";

    private final MessageMapper messageMapper;
    private final MQService mqService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void sendMessage(Long senderId, SendMessageDTO dto) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(dto.getReceiverId());
        message.setContent(dto.getContent());
        message.setStatus(0);
        messageMapper.insert(message);

        redisTemplate.opsForValue().increment(UNREAD_KEY + dto.getReceiverId(), 1);

        mqService.sendMessageNotify(new MessageNotifyMessage(dto.getReceiverId(), "message", message.getId(), dto.getContent()));
    }

    @Override
    public IPage<MessageVO> listMessages(Long userId, Long targetId, int page, int size) {
        Page<Message> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getReceiverId, targetId)
                        .or()
                        .eq(Message::getSenderId, targetId).eq(Message::getReceiverId, userId))
                .orderByDesc(Message::getCreateTime);
        IPage<Message> result = messageMapper.selectPage(pageParam, qw);
        return result.convert(this::toVO);
    }

    @Override
    public void markRead(Long userId, Long targetId) {
        messageMapper.update(null, new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getSenderId, targetId)
                .eq(Message::getStatus, 0)
                .set(Message::getStatus, 1));
        redisTemplate.delete(UNREAD_KEY + userId);
    }

    @Override
    public Long getUnreadCount(Long userId) {
        Object val = redisTemplate.opsForValue().get(UNREAD_KEY + userId);
        if (val instanceof Number) return ((Number) val).longValue();
        Long cnt = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getStatus, 0));
        redisTemplate.opsForValue().set(UNREAD_KEY + userId, cnt);
        return cnt;
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
