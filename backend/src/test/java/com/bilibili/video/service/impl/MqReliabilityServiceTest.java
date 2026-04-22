package com.bilibili.video.service.impl;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.exception.ManualInterventionMqException;
import com.bilibili.video.exception.NonRetryableMqException;
import com.bilibili.video.exception.RetryableMqException;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqReliabilityServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

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
        lenient().when(redisTemplate.opsForHash()).thenReturn(null);
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> kvStore.containsKey(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            kvStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldSkipDuplicateEventId() {
        SearchSyncMessage first = new SearchSyncMessage("video", 1L, "update");
        setField(first, "eventId", "evt-1");
        setField(first, "bizKey", "search:video:1:update");
        setField(first, "eventType", "update");

        SearchSyncMessage duplicate = new SearchSyncMessage("video", 1L, "update");
        setField(duplicate, "eventId", "evt-1");
        setField(duplicate, "bizKey", "search:video:1:update");
        setField(duplicate, "eventType", "update");

        AtomicInteger handled = new AtomicInteger();
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, first, handled::incrementAndGet);
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, duplicate, handled::incrementAndGet);

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void shouldSkipSameBusinessEventWithDifferentEventIds() {
        SearchSyncMessage first = new SearchSyncMessage("video", 99L, "delete");
        setField(first, "eventId", "evt-1");
        setField(first, "bizKey", "search:video:99:delete");
        setField(first, "eventType", "delete");

        SearchSyncMessage duplicateBusinessEvent = new SearchSyncMessage("video", 99L, "delete");
        setField(duplicateBusinessEvent, "eventId", "evt-2");
        setField(duplicateBusinessEvent, "bizKey", "search:video:99:delete");
        setField(duplicateBusinessEvent, "eventType", "delete");

        AtomicInteger handled = new AtomicInteger();
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, first, handled::incrementAndGet);
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, duplicateBusinessEvent, handled::incrementAndGet);

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void shouldSwallowNonRetryableExceptionAndMarkEventAsHandled() {
        SearchSyncMessage message = new SearchSyncMessage("video", 7L, "update");
        setField(message, "eventId", "evt-non-retryable");
        setField(message, "bizKey", "search:video:7:update");
        setField(message, "eventType", "update");

        AtomicInteger handled = new AtomicInteger();
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, () -> {
            handled.incrementAndGet();
            throw new NonRetryableMqException("invalid payload");
        });
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, handled::incrementAndGet);

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void shouldSwallowManualInterventionExceptionAndMarkEventAsHandled() {
        SearchSyncMessage message = new SearchSyncMessage("video", 8L, "update");
        setField(message, "eventId", "evt-manual");
        setField(message, "bizKey", "search:video:8:update");
        setField(message, "eventType", "update");

        AtomicInteger handled = new AtomicInteger();
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, () -> {
            handled.incrementAndGet();
            throw new ManualInterventionMqException("need manual review");
        });
        mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, handled::incrementAndGet);

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void shouldPropagateRetryableExceptionWithoutMarkingDedup() {
        SearchSyncMessage message = new SearchSyncMessage("video", 9L, "update");
        setField(message, "eventId", "evt-retryable");
        setField(message, "bizKey", "search:video:9:update");
        setField(message, "eventType", "update");

        AtomicInteger handled = new AtomicInteger();
        assertThatThrownBy(() -> mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, () -> {
            handled.incrementAndGet();
            throw new RetryableMqException("es unavailable");
        })).isInstanceOf(RetryableMqException.class);

        assertThatThrownBy(() -> mqReliabilityService.consumeWithIdempotency(MqTopics.SEARCH_SYNC, message, () -> {
            handled.incrementAndGet();
            throw new RetryableMqException("es unavailable");
        })).isInstanceOf(RetryableMqException.class);

        assertThat(handled.get()).isEqualTo(2);
    }

    private void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
        assertThat(ReflectionTestUtils.getField(target, fieldName)).isEqualTo(value);
    }
}
