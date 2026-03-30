package com.bilibili.video.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.model.dto.SendMessageDTO;
import com.bilibili.video.model.vo.MessageConversationVO;
import com.bilibili.video.model.vo.MessageVO;

public interface MessageService {

    void sendMessage(Long senderId, SendMessageDTO dto);

    IPage<MessageVO> listMessages(Long userId, Long targetId, int page, int size);

    void markRead(Long userId, Long targetId);

    Long getUnreadCount(Long userId);

    java.util.List<MessageConversationVO> listConversations(Long userId);

    void revokeMessage(Long userId, Long messageId);

    void clearConversation(Long userId, Long targetId);
}
