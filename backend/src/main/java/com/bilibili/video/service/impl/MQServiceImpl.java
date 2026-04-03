package com.bilibili.video.service.impl;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.model.mq.VideoSemanticIndexMessage;
import com.bilibili.video.service.MQService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MQServiceImpl implements MQService {

    private static final String LOG_TEMPLATE = "[MQ] {} message send failed, topic={}, payload={}, attempt={}";

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectMapper objectMapper;

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
    public void sendVideoSemanticIndex(VideoSemanticIndexMessage message) {
        sendAsyncWithRetry(MqTopics.VIDEO_SEMANTIC_INDEX, message, "video semantic index");
    }

    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        sendAsyncWithRetry(MqTopics.MESSAGE_NOTIFY, message, "message notify");
    }

    private void sendAsyncWithRetry(String topic, Object payload, String scene) {
        sendAsyncWithRetry(topic, payload, scene, 1);
    }

    private void sendAsyncWithRetry(String topic, Object payload, String scene, int attempt) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] skip send because RocketMQTemplate unavailable, topic={}, payload={}", topic, payload);
            persistProducerDeadLetter(topic, payload, scene, attempt, "template unavailable");
            return;
        }

        try {
            template.asyncSend(topic, payload, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (log.isDebugEnabled()) {
                        log.debug("[MQ] {} async send success, topic={}, payload={}, attempt={}, result={}",
                                scene, topic, payload, attempt, sendResult);
                    }
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

    private void handleSendFailure(String topic, Object payload, String scene, int attempt, Throwable e) {
        log.error(LOG_TEMPLATE, scene, topic, payload, attempt, e);
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

    private void persistProducerDeadLetter(String topic, Object payload, String scene, int attempt, String reason) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("scene", scene);
            record.put("topic", topic);
            record.put("attempt", attempt);
            record.put("reason", reason);
            record.put("occurredAt", LocalDateTime.now().toString());
            record.put("payload", payload);
            redisTemplate.opsForList().leftPush(producerDeadLetterKey, objectMapper.writeValueAsString(record));
            redisTemplate.opsForList().trim(producerDeadLetterKey, 0, 1999);
        } catch (Exception ex) {
            log.warn("[MQ] failed to persist producer dead letter, topic={}, payload={}", topic, payload, ex);
        }
    }
}
