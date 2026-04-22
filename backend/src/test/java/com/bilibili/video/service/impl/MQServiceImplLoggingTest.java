package com.bilibili.video.service.impl;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MQServiceImplLoggingTest {

    @Mock
    private ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    @Mock
    private ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private MQServiceImpl mqService;

    @BeforeEach
    void setUp() {
        mqService = new MQServiceImpl(rocketMQTemplateProvider, redisTemplateProvider, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(mqService, "maxRetries", 1);
        ReflectionTestUtils.setField(mqService, "retryDelayMs", 100L);
        ReflectionTestUtils.setField(mqService, "producerDeadLetterKey", "mq:producer:dead-letter");
        ReflectionTestUtils.setField(mqService, "loggerDelegate", mock(MQStructuredLogger.class));
    }

    @Test
    void shouldLogStructuredSendSuccess() {
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(rocketMQTemplate);
        SearchSyncMessage message = new SearchSyncMessage("video", 1L, "create");
        MQStructuredLogger logger = mock(MQStructuredLogger.class);
        ReflectionTestUtils.setField(mqService, "loggerDelegate", logger);

        doAnswerSendSuccess(message);
        mqService.sendSearchSync(message);

        verify(logger).logProducerSuccess(eq(MqTopics.SEARCH_SYNC), eq("search sync"), eq(message), eq(1));
    }

    @Test
    void shouldLogStructuredDeadLetterReplaySuccess() throws Exception {
        SearchSyncMessage message = new SearchSyncMessage("video", 2L, "update");
        ReflectionTestUtils.setField(message, "eventId", "evt-log-1");
        ReflectionTestUtils.setField(message, "bizKey", "search:video:2:update");
        ReflectionTestUtils.setField(message, "traceId", "trace-1");
        ReflectionTestUtils.setField(message, "eventType", "update");
        ReflectionTestUtils.setField(message, "version", 1);
        ReflectionTestUtils.setField(message, "occurredAt", java.time.LocalDateTime.now());

        MQStructuredLogger logger = mock(MQStructuredLogger.class);
        ReflectionTestUtils.setField(mqService, "loggerDelegate", logger);
        Method method = MQServiceImpl.class.getDeclaredMethod("logDeadLetterReplaySuccess", String.class, MQServiceImpl.DeadLetterRecord.class, com.bilibili.video.model.mq.BaseMqMessage.class);
        method.setAccessible(true);

        MQServiceImpl.DeadLetterRecord record = buildRecord();
        method.invoke(mqService, MqTopics.SEARCH_SYNC, record, message);

        verify(logger).logCompensationSuccess(eq(MqTopics.SEARCH_SYNC), eq("search sync"), eq(message));
    }

    private void doAnswerSendSuccess(SearchSyncMessage message) {
        doAnswer(invocation -> {
            SendCallback callback = invocation.getArgument(2);
            callback.onSuccess(null);
            return null;
        }).when(rocketMQTemplate).asyncSend(eq(MqTopics.SEARCH_SYNC), eq(message), any(SendCallback.class));
    }

    private MQServiceImpl.DeadLetterRecord buildRecord() {
        MQServiceImpl.DeadLetterRecord record = new MQServiceImpl.DeadLetterRecord();
        record.setEventId("evt-log-1");
        record.setBizKey("search:video:2:update");
        record.setTopic(MqTopics.SEARCH_SYNC);
        record.setScene("search sync");
        record.setStatus("PENDING");
        return record;
    }
}
