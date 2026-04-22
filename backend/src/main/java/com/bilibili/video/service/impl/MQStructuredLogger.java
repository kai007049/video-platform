package com.bilibili.video.service.impl;

import com.bilibili.video.model.mq.BaseMqMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MQStructuredLogger {

    public void logProducerSuccess(String topic, String scene, BaseMqMessage message, int attempt) {
        log.info("[MQ] producer status=SUCCESS topic={} scene={} eventId={} bizKey={} traceId={} eventType={} version={} attempt={}",
                topic,
                scene,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                message == null ? null : message.getEventType(),
                message == null ? null : message.getVersion(),
                attempt);
    }

    public void logProducerFailure(String topic, String scene, BaseMqMessage message, int attempt, Throwable error) {
        log.error("[MQ] producer status=FAILURE topic={} scene={} eventId={} bizKey={} traceId={} attempt={} reason={}",
                topic,
                scene,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                attempt,
                error == null ? null : error.getMessage(),
                error);
    }

    public void logProducerDeadLetter(String topic, String scene, BaseMqMessage message, int attempt, String reason) {
        log.warn("[MQ] producer status=DEAD_LETTER topic={} scene={} eventId={} bizKey={} traceId={} attempt={} reason={}",
                topic,
                scene,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                attempt,
                reason);
    }

    public void logCompensationSuccess(String topic, String scene, BaseMqMessage message) {
        log.info("[MQ] compensation status=SUCCESS topic={} scene={} eventId={} bizKey={} traceId={}",
                topic,
                scene,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId());
    }

    public void logCompensationFailure(String topic, String scene, BaseMqMessage message, int compensationAttempts, Throwable error) {
        log.warn("[MQ] compensation status=FAILURE topic={} scene={} eventId={} bizKey={} traceId={} compensationAttempts={} reason={}",
                topic,
                scene,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                compensationAttempts,
                error == null ? null : error.getMessage(),
                error);
    }

    public void logConsumerDuplicate(String topic, String consumerGroup, String dedupType, String dedupKey, BaseMqMessage message) {
        log.info("[MQ] consumer status=DUPLICATE topic={} consumerGroup={} dedupType={} dedupKey={} eventId={} bizKey={} traceId={}",
                topic,
                consumerGroup,
                dedupType,
                dedupKey,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId());
    }

    public void logConsumerSuccess(String topic, String consumerGroup, String eventDedupKey, String bizDedupKey, BaseMqMessage message) {
        log.info("[MQ] consumer status=SUCCESS topic={} consumerGroup={} eventDedupKey={} bizDedupKey={} eventId={} bizKey={} traceId={}",
                topic,
                consumerGroup,
                eventDedupKey,
                bizDedupKey,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId());
    }

    public void logConsumerTerminal(String topic, String consumerGroup, String eventDedupKey, String bizDedupKey, BaseMqMessage message, String status, String reason) {
        log.warn("[MQ] consumer status={} topic={} consumerGroup={} eventDedupKey={} bizDedupKey={} eventId={} bizKey={} traceId={} reason={}",
                status,
                topic,
                consumerGroup,
                eventDedupKey,
                bizDedupKey,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                reason);
    }

    public void logConsumerFailurePersistError(String topic, String consumerGroup, BaseMqMessage message, Exception error) {
        log.warn("[MQ] consumer status=FAILURE_RECORD_ERROR topic={} consumerGroup={} eventId={} bizKey={} traceId={} reason={}",
                topic,
                consumerGroup,
                message == null ? null : message.getEventId(),
                message == null ? null : message.getBizKey(),
                message == null ? null : message.getTraceId(),
                error == null ? null : error.getMessage(),
                error);
    }
}
