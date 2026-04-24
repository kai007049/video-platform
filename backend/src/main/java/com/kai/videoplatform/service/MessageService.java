package com.kai.videoplatform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.model.dto.SendMessageDTO;
import com.kai.videoplatform.model.vo.MessageConversationVO;
import com.kai.videoplatform.model.vo.MessageVO;

public interface MessageService {

    void sendMessage(Long senderId, SendMessageDTO dto);

    IPage<MessageVO> listMessages(Long userId, Long targetId, int page, int size);

    void markRead(Long userId, Long targetId);

    Long getUnreadCount(Long userId);

    java.util.List<MessageConversationVO> listConversations(Long userId);

    void revokeMessage(Long userId, Long messageId);

    void clearConversation(Long userId, Long targetId);
}