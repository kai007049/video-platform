package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.model.mq.BaseMqMessage;
import com.kai.videoplatform.model.mq.DanmuMessage;
import com.kai.videoplatform.model.mq.MessageNotifyMessage;
import com.kai.videoplatform.model.mq.NotifyMessage;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.kai.videoplatform.model.mq.VideoDeleteMessage;
import com.kai.videoplatform.model.mq.VideoProcessMessage;
import com.kai.videoplatform.service.MQService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MQServiceImpl implements MQService {

    private static final int MESSAGE_VERSION = 1;

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private MQStructuredLogger loggerDelegate = new MQStructuredLogger();

    @Value("${mq.reliability.producer.max-retries:3}")
    private int maxRetries;

    @Value("${mq.reliability.producer.retry-delay-ms:1500}")
    private long retryDelayMs;

    @Value("${mq.reliability.producer.dead-letter-key:mq:producer:dead-letter}")
    private String producerDeadLetterKey;

    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "mq-producer-retry");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void logRocketMQTemplateStatus() {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] RocketMQTemplate is unavailable, MQ sends will be skipped.");
        } else {
            log.info("[MQ] RocketMQTemplate is ready. producerMaxRetries={}, retryDelayMs={}",
                    maxRetries, retryDelayMs);
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        retryScheduler.shutdownNow();
    }

    @Override
    public void sendVideoProcess(VideoProcessMessage message) {
        sendAsyncWithRetry(MqTopics.VIDEO_PROCESS, message, "video process");
    }

    @Override
    public void sendVideoCoverProcess(VideoProcessMessage message) {
        sendAsyncWithRetry(MqTopics.VIDEO_COVER_PROCESS, message, "video cover process");
    }

    @Override
    public void sendDanmu(DanmuMessage message) {
        sendAsyncWithRetry(MqTopics.DANMU_PROCESS, message, "danmu process");
    }

    @Override
    public void sendNotify(NotifyMessage message) {
        sendAsyncWithRetry(MqTopics.NOTIFY_EVENT, message, "notify event");
    }

    @Override
    public void sendSearchSync(SearchSyncMessage message) {
        sendAsyncWithRetry(MqTopics.SEARCH_SYNC, message, "search sync");
    }

    @Override
    public void sendVideoDelete(VideoDeleteMessage message) {
        sendAsyncWithRetry(MqTopics.VIDEO_DELETE, message, "video delete");
    }

    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        sendAsyncWithRetry(MqTopics.MESSAGE_NOTIFY, message, "message notify");
    }

    @Scheduled(fixedDelayString = "${mq.reliability.producer.compensation-interval-ms:5000}")
    public void retryDueDeadLetters() {
        retryDueDeadLetters(System.currentTimeMillis());
    }

    private void sendAsyncWithRetry(String topic, BaseMqMessage payload, String scene) {
        fillMessageMetadata(topic, payload);
        sendAsyncWithRetry(topic, payload, scene, 1);
    }

    private void sendAsyncWithRetry(String topic, BaseMqMessage payload, String scene, int attempt) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            persistProducerDeadLetter(topic, payload, scene, attempt, "template unavailable");
            return;
        }

        try {
            template.asyncSend(topic, payload, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    loggerDelegate.logProducerSuccess(topic, scene, payload, attempt);
                }

                @Override
                public void onException(Throwable e) {
                    handleSendFailure(topic, payload, scene, attempt, e);
                }
            });
        } catch (Exception e) {
            handleSendFailure(topic, payload, scene, attempt, e);
        }
    }

    private void handleSendFailure(String topic, BaseMqMessage payload, String scene, int attempt, Throwable e) {
        loggerDelegate.logProducerFailure(topic, scene, payload, attempt, e);
        if (attempt < maxRetries) {
            long nextDelay = retryDelayMs * (1L << (attempt - 1));
            retryScheduler.schedule(
                    () -> sendAsyncWithRetry(topic, payload, scene, attempt + 1),
                    nextDelay,
                    TimeUnit.MILLISECONDS
            );
            return;
        }
        persistProducerDeadLetter(topic, payload, scene, attempt, e.getMessage());
    }

    private void fillMessageMetadata(String topic, BaseMqMessage message) {
        if (message.getEventId() == null || message.getEventId().isBlank()) {
            message.setEventId(UUID.randomUUID().toString());
        }
        if (message.getTraceId() == null || message.getTraceId().isBlank()) {
            message.setTraceId(message.getEventId());
        }
        if (message.getOccurredAt() == null) {
            message.setOccurredAt(LocalDateTime.now());
        }
        if (message.getVersion() == null) {
            message.setVersion(MESSAGE_VERSION);
        }
        if (message.getEventType() == null || message.getEventType().isBlank()) {
            message.setEventType(resolveEventType(topic, message));
        }
        if (message.getBizKey() == null || message.getBizKey().isBlank()) {
            message.setBizKey(resolveBizKey(topic, message));
        }
    }

    private String resolveEventType(String topic, BaseMqMessage message) {
        if (message instanceof SearchSyncMessage searchSyncMessage && searchSyncMessage.getAction() != null) {
            return searchSyncMessage.getAction();
        }
        if (message instanceof NotifyMessage notifyMessage && notifyMessage.getType() != null) {
            return notifyMessage.getType();
        }
        if (message instanceof MessageNotifyMessage messageNotifyMessage && messageNotifyMessage.getType() != null) {
            return messageNotifyMessage.getType();
        }
        return topic;
    }

    private String resolveBizKey(String topic, BaseMqMessage message) {
        if (message instanceof SearchSyncMessage searchSyncMessage) {
            return "search:" + searchSyncMessage.getEntityType() + ":" + searchSyncMessage.getEntityId() + ":" + searchSyncMessage.getAction();
        }
        if (message instanceof VideoProcessMessage videoProcessMessage) {
            return topic + ":video:" + videoProcessMessage.getVideoId();
        }
        if (message instanceof VideoDeleteMessage videoDeleteMessage) {
            return "video:" + videoDeleteMessage.getVideoId() + ":delete";
        }
        if (message instanceof NotifyMessage notifyMessage) {
            return "notify:user:" + notifyMessage.getUserId() + ":" + notifyMessage.getType() + ":" + notifyMessage.getTargetId();
        }
        if (message instanceof MessageNotifyMessage messageNotifyMessage) {
            return "message:user:" + messageNotifyMessage.getReceiverId() + ":" + messageNotifyMessage.getType() + ":" + messageNotifyMessage.getRefId();
        }
        if (message instanceof DanmuMessage danmuMessage) {
            return "danmu:video:" + danmuMessage.getVideoId() + ":user:" + danmuMessage.getUserId() + ":" + danmuMessage.getTimePoint();
        }
        return topic + ":" + message.getEventId();
    }

    private void persistProducerDeadLetter(String topic, BaseMqMessage payload, String scene, int attempt, String reason) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        loggerDelegate.logProducerDeadLetter(topic, scene, payload, attempt, reason);
        if (redisTemplate == null) {
            return;
        }
        try {
            DeadLetterRecord record = new DeadLetterRecord();
            record.setEventId(payload.getEventId());
            record.setBizKey(payload.getBizKey());
            record.setTraceId(payload.getTraceId());
            record.setTopic(topic);
            record.setScene(scene);
            record.setPayloadClass(payload.getClass().getName());
            record.setPayloadJson(objectMapper.writeValueAsString(payload));
            record.setStatus("PENDING");
            record.setRetryCount(attempt);
            record.setCompensationAttempts(0);
            record.setReason(reason);
            record.setOccurredAt(LocalDateTime.now());
            record.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.max(5, retryDelayMs / 1000)));

            HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
            String hashKey = producerDeadLetterKey + ":records";
            String scheduleKey = producerDeadLetterKey + ":schedule";
            hashOperations.put(hashKey, record.getEventId(), objectMapper.writeValueAsString(record));
            redisTemplate.opsForZSet().add(scheduleKey, record.getEventId(), toScore(record.getNextRetryAt()));
        } catch (Exception ex) {
            loggerDelegate.logProducerFailure(topic, scene, payload, attempt, ex);
        }
    }

    private void retryDueDeadLetters(long nowMillis) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (redisTemplate == null || template == null) {
            return;
        }
        String hashKey = producerDeadLetterKey + ":records";
        String scheduleKey = producerDeadLetterKey + ":schedule";
        Set<Object> dueEventIds = redisTemplate.opsForZSet().rangeByScore(scheduleKey, 0, nowMillis, 0, 20);
        if (dueEventIds == null || dueEventIds.isEmpty()) {
            return;
        }
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        for (Object rawEventId : dueEventIds) {
            String eventId = String.valueOf(rawEventId);
            Object rawRecord = hashOperations.get(hashKey, eventId);
            if (rawRecord == null) {
                redisTemplate.opsForZSet().remove(scheduleKey, eventId);
                continue;
            }
            try {
                DeadLetterRecord record = objectMapper.readValue(String.valueOf(rawRecord), DeadLetterRecord.class);
                BaseMqMessage payload = (BaseMqMessage) objectMapper.readValue(record.getPayloadJson(), Class.forName(record.getPayloadClass()));
                template.syncSend(record.getTopic(), payload);
                hashOperations.delete(hashKey, eventId);
                redisTemplate.opsForZSet().remove(scheduleKey, eventId);
                logDeadLetterReplaySuccess(record.getTopic(), record, payload);
            } catch (Exception ex) {
                try {
                    DeadLetterRecord record = objectMapper.readValue(String.valueOf(rawRecord), DeadLetterRecord.class);
                    record.setCompensationAttempts(record.getCompensationAttempts() + 1);
                    record.setReason(ex.getMessage());
                    record.setOccurredAt(LocalDateTime.now());
                    BaseMqMessage payload = null;
                    try {
                        payload = (BaseMqMessage) objectMapper.readValue(record.getPayloadJson(), Class.forName(record.getPayloadClass()));
                    } catch (Exception ignored) {
                    }
                    loggerDelegate.logCompensationFailure(record.getTopic(), record.getScene(), payload, record.getCompensationAttempts(), ex);
                    if (record.getCompensationAttempts() >= maxRetries) {
                        record.setStatus("FINAL_FAILED");
                        redisTemplate.opsForZSet().remove(scheduleKey, eventId);
                    } else {
                        record.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.max(5, retryDelayMs / 1000)));
                        redisTemplate.opsForZSet().add(scheduleKey, eventId, toScore(record.getNextRetryAt()));
                    }
                    hashOperations.put(hashKey, eventId, objectMapper.writeValueAsString(record));
                } catch (Exception nestedEx) {
                    log.warn("[MQ] dead letter replay failed to update state, eventId={}", eventId, nestedEx);
                }
            }
        }
    }

    private void logDeadLetterReplaySuccess(String topic, DeadLetterRecord record, BaseMqMessage payload) {
        loggerDelegate.logCompensationSuccess(topic, record.getScene(), payload);
    }

    private double toScore(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Data
    static class DeadLetterRecord {
        private String eventId;
        private String bizKey;
        private String traceId;
        private String topic;
        private String scene;
        private String payloadClass;
        private String payloadJson;
        private String status;
        private Integer retryCount;
        private Integer compensationAttempts;
        private String reason;
        private LocalDateTime occurredAt;
        private LocalDateTime nextRetryAt;
    }
}