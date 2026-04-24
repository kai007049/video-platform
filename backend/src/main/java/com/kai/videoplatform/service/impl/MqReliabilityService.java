package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.exception.ManualInterventionMqException;
import com.kai.videoplatform.exception.NonRetryableMqException;
import com.kai.videoplatform.exception.RetryableMqException;
import com.kai.videoplatform.model.mq.BaseMqMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqReliabilityService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private MQStructuredLogger loggerDelegate = new MQStructuredLogger();

    @Value("${mq.reliability.consumer.dedup-prefix:mq:consume:done}")
    private String consumeDedupPrefix;

    @Value("${mq.reliability.consumer.dedup-ttl-hours:168}")
    private long consumeDedupTtlHours;

    @Value("${mq.reliability.consumer.failure-record-key:mq:consumer:failures}")
    private String consumerFailureRecordKey;

    public void consumeWithIdempotency(String topic, Object payload, Runnable handler) {
        consumeWithIdempotency(topic, "default-consumer", payload, handler);
    }

    public void consumeWithIdempotency(String topic, String consumerGroup, Object payload, Runnable handler) {
        String eventDedupKey = buildEventDedupKey(topic, payload);
        String bizDedupKey = buildBusinessDedupKey(topic, payload);
        BaseMqMessage baseMessage = payload instanceof BaseMqMessage message ? message : null;

        if (eventDedupKey != null && Boolean.TRUE.equals(redisTemplate.hasKey(eventDedupKey))) {
            loggerDelegate.logConsumerDuplicate(topic, consumerGroup, "eventId", eventDedupKey, baseMessage);
            return;
        }
        if (bizDedupKey != null && Boolean.TRUE.equals(redisTemplate.hasKey(bizDedupKey))) {
            loggerDelegate.logConsumerDuplicate(topic, consumerGroup, "bizKey", bizDedupKey, baseMessage);
            return;
        }

        try {
            handler.run();
            markHandled(eventDedupKey, bizDedupKey);
            loggerDelegate.logConsumerSuccess(topic, consumerGroup, eventDedupKey, bizDedupKey, baseMessage);
        } catch (RetryableMqException e) {
            throw e;
        } catch (NonRetryableMqException | ManualInterventionMqException e) {
            markHandled(eventDedupKey, bizDedupKey);
            persistConsumerFailure(topic, consumerGroup, payload, e);
            loggerDelegate.logConsumerTerminal(
                    topic,
                    consumerGroup,
                    eventDedupKey,
                    bizDedupKey,
                    baseMessage,
                    e instanceof ManualInterventionMqException ? "MANUAL_INTERVENTION" : "NON_RETRYABLE",
                    e.getMessage()
            );
        }
    }

    private void persistConsumerFailure(String topic, String consumerGroup, Object payload, RuntimeException exception) {
        BaseMqMessage baseMessage = payload instanceof BaseMqMessage message ? message : null;
        try {
            ConsumerFailureRecord record = new ConsumerFailureRecord();
            record.setRecordId(UUID.randomUUID().toString());
            record.setTopic(topic);
            record.setConsumerGroup(consumerGroup);
            if (baseMessage != null) {
                record.setEventId(baseMessage.getEventId());
                record.setBizKey(baseMessage.getBizKey());
                record.setTraceId(baseMessage.getTraceId());
            }
            record.setPayloadJson(objectMapper.writeValueAsString(payload));
            record.setErrorMessage(exception.getMessage());
            record.setStatus(exception instanceof ManualInterventionMqException ? "MANUAL_INTERVENTION" : "NON_RETRYABLE");
            record.setRecordedAt(LocalDateTime.now());
            HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
            hashOperations.put(consumerFailureRecordKey, record.getRecordId(), objectMapper.writeValueAsString(record));
        } catch (Exception nestedEx) {
            loggerDelegate.logConsumerFailurePersistError(topic, consumerGroup, baseMessage, nestedEx);
        }
    }

    private void markHandled(String eventDedupKey, String bizDedupKey) {
        Duration ttl = Duration.ofHours(consumeDedupTtlHours);
        if (eventDedupKey != null) {
            redisTemplate.opsForValue().set(eventDedupKey, "1", ttl);
        }
        if (bizDedupKey != null) {
            redisTemplate.opsForValue().set(bizDedupKey, "1", ttl);
        }
    }

    private String buildEventDedupKey(String topic, Object payload) {
        if (payload instanceof BaseMqMessage message && message.getEventId() != null && !message.getEventId().isBlank()) {
            return consumeDedupPrefix + ":event:" + topic + ":" + message.getEventId();
        }
        return null;
    }

    private String buildBusinessDedupKey(String topic, Object payload) {
        if (payload instanceof BaseMqMessage message && message.getBizKey() != null && !message.getBizKey().isBlank()) {
            return consumeDedupPrefix + ":biz:" + topic + ":" + message.getBizKey();
        }
        String payloadHash = digestPayload(payload);
        return consumeDedupPrefix + ":hash:" + topic + ":" + payloadHash;
    }

    private String digestPayload(Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            String fallback = String.valueOf(payload);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(fallback.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                return Integer.toHexString(fallback.hashCode());
            }
        }
    }

    @Data
    private static class ConsumerFailureRecord {
        private String recordId;
        private String topic;
        private String consumerGroup;
        private String eventId;
        private String bizKey;
        private String traceId;
        private String payloadJson;
        private String errorMessage;
        private String status;
        private LocalDateTime recordedAt;
    }
}