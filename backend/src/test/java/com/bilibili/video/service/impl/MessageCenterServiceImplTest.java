package com.bilibili.video.service.impl;

import com.bilibili.video.entity.Notification;
import com.bilibili.video.mapper.MessageMapper;
import com.bilibili.video.mapper.NotificationMapper;
import com.bilibili.video.model.vo.MessageSummaryVO;
import com.bilibili.video.service.MessageService;
import com.bilibili.video.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageCenterServiceImplTest {

    @Test
    void summary_shouldNotDoubleCountSystemUnreadInTotal() {
        MessageService messageService = mock(MessageService.class);
        NotificationService notificationService = mock(NotificationService.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        MessageCenterServiceImpl service = new MessageCenterServiceImpl(
                messageService,
                notificationService,
                messageMapper,
                notificationMapper
        );

        when(messageService.getUnreadCount(10L)).thenReturn(2L);
        when(notificationService.getUnreadCount(10L)).thenReturn(5L);
        when(notificationMapper.selectCount(any())).thenReturn(3L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(notificationMapper.selectList(any())).thenReturn(List.<Notification>of());

        MessageSummaryVO summary = service.summary(10L);

        assertThat(summary.getMessageUnread()).isEqualTo(2L);
        assertThat(summary.getNotificationUnread()).isEqualTo(5L);
        assertThat(summary.getSystemUnread()).isEqualTo(3L);
        assertThat(summary.getTotalUnread()).isEqualTo(7L);
    }
}
