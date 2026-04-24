package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MQServiceImplTest {

    @Mock
    private ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    @Mock
    private ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private MQServiceImpl mqService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mqService = new MQServiceImpl(rocketMQTemplateProvider, redisTemplateProvider, objectMapper);
        ReflectionTestUtils.setField(mqService, "maxRetries", 1);
        ReflectionTestUtils.setField(mqService, "retryDelayMs", 100L);
        ReflectionTestUtils.setField(mqService, "producerDeadLetterKey", "mq:producer:dead-letter");
    }

    @Test
    void shouldPopulateStandardMetadataBeforeAsyncSend() {
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(rocketMQTemplate);
        AtomicReference<Object> capturedPayload = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedPayload.set(invocation.getArgument(1));
            return null;
        }).when(rocketMQTemplate).asyncSend(eq(MqTopics.SEARCH_SYNC), any(SearchSyncMessage.class), any(SendCallback.class));

        SearchSyncMessage message = new SearchSyncMessage("video", 1L, "create");
        mqService.sendSearchSync(message);

        assertThat(readField(message, "eventId")).isNotNull();
        assertThat(readField(message, "bizKey")).isEqualTo("search:video:1:create");
        assertThat(readField(message, "traceId")).isNotNull();
        assertThat(readField(message, "eventType")).isEqualTo("create");
        assertThat(readField(message, "version")).isEqualTo(1);
        assertThat(readField(message, "occurredAt")).isNotNull();
        assertThat(capturedPayload.get()).isSameAs(message);
    }

    @Test
    void shouldPersistDeadLetterIntoHashAndScheduleSetWhenTemplateUnavailable() {
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(null);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        SearchSyncMessage message = new SearchSyncMessage("video", 2L, "update");
        mqService.sendSearchSync(message);

        verify(hashOperations).put(anyString(), anyString(), anyString());
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void shouldReplayDueDeadLetterAndClearStoredStateOnSuccess() throws Exception {
        when(rocketMQTemplateProvider.getIfAvailable()).thenReturn(rocketMQTemplate);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of("evt-1"));

        String payloadJson = objectMapper.writeValueAsString(new SearchSyncMessage("video", 3L, "delete"));
        String escapedPayloadJson = objectMapper.writeValueAsString(payloadJson);
        String recordJson = "{" +
                "\"eventId\":\"evt-1\"," +
                "\"bizKey\":\"search:video:3:delete\"," +
                "\"topic\":\"search_sync\"," +
                "\"scene\":\"search sync\"," +
                "\"payloadClass\":\"com.kai.videoplatform.model.mq.SearchSyncMessage\"," +
                "\"payloadJson\":" + escapedPayloadJson + "," +
                "\"status\":\"PENDING\"," +
                "\"retryCount\":1," +
                "\"compensationAttempts\":0," +
                "\"reason\":\"network\"" +
                "}";
        when(hashOperations.get(anyString(), eq("evt-1"))).thenReturn(recordJson);
        when(rocketMQTemplate.syncSend(eq(MqTopics.SEARCH_SYNC), any(SearchSyncMessage.class)))
                .thenReturn(mock(SendResult.class));

        invokeRetryDueDeadLetters(mqService, System.currentTimeMillis());

        verify(rocketMQTemplate).syncSend(eq(MqTopics.SEARCH_SYNC), any(SearchSyncMessage.class));
        verify(hashOperations).delete(anyString(), eq("evt-1"));
        verify(zSetOperations).remove(anyString(), eq("evt-1"));
    }

    private Object readField(Object target, String fieldName) {
        Object value = ReflectionTestUtils.getField(target, fieldName);
        assertThat(value).as(fieldName).isNotNull();
        return value;
    }

    private void invokeRetryDueDeadLetters(MQServiceImpl service, long nowMillis) throws Exception {
        Method method = MQServiceImpl.class.getDeclaredMethod("retryDueDeadLetters", long.class);
        method.setAccessible(true);
        method.invoke(service, nowMillis);
    }
}