package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.exception.ManualInterventionMqException;
import com.kai.videoplatform.exception.NonRetryableMqException;
import com.kai.videoplatform.exception.RetryableMqException;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqReliabilityServiceConsumerFailureRecordTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private MqReliabilityService mqReliabilityService;
    private Map<String, Object> kvStore;

    @BeforeEach
    void setUp() {
        mqReliabilityService = new MqReliabilityService(redisTemplate, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(mqReliabilityService, "consumeDedupPrefix", "mq:consume:done");
        ReflectionTestUtils.setField(mqReliabilityService, "consumeDedupTtlHours", 168L);
        ReflectionTestUtils.setField(mqReliabilityService, "consumerFailureRecordKey", "mq:consumer:failures");

        kvStore = new HashMap<>();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> kvStore.containsKey(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            kvStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldRecordNonRetryableFailureWithConsumerGroup() {
        SearchSyncMessage message = message("evt-non-retry-record", "search:video:11:update");

        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, "search-sync-consumer", message, () -> {
            throw new NonRetryableMqException("invalid payload");
        });

        verify(hashOperations).put(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRecordManualInterventionFailureWithConsumerGroup() {
        SearchSyncMessage message = message("evt-manual-record", "search:video:12:update");

        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, "search-sync-consumer", message, () -> {
            throw new ManualInterventionMqException("manual review required");
        });

        verify(hashOperations).put(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotRecordRetryableFailureBecauseItWillBeRetried() {
        SearchSyncMessage message = message("evt-retry-record", "search:video:13:update");
        AtomicInteger handled = new AtomicInteger();

        assertThatThrownBy(() -> mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, "search-sync-consumer", message, () -> {
            handled.incrementAndGet();
            throw new RetryableMqException("temporary unavailable");
        })).isInstanceOf(RetryableMqException.class);

        assertThat(handled.get()).isEqualTo(1);
    }

    private SearchSyncMessage message(String eventId, String bizKey) {
        SearchSyncMessage message = new SearchSyncMessage("video", 1L, "update");
        ReflectionTestUtils.setField(message, "eventId", eventId);
        ReflectionTestUtils.setField(message, "bizKey", bizKey);
        ReflectionTestUtils.setField(message, "eventType", "update");
        return message;
    }
}