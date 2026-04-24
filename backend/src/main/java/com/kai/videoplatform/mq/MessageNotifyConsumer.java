package com.kai.videoplatform.mq;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.model.mq.MessageNotifyMessage;
import com.kai.videoplatform.service.impl.MqReliabilityService;
import com.kai.videoplatform.ws.MessageWebSocketServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.MESSAGE_NOTIFY,
        consumerGroup = "message-notify-consumer",
        maxReconsumeTimes = 5
)
public class MessageNotifyConsumer implements RocketMQListener<MessageNotifyMessage> {

    private final MessageWebSocketServer messageWebSocketServer;
    private final MqReliabilityService mqReliabilityService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageNotifyMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.MESSAGE_NOTIFY, "message-notify-consumer", message, () -> {
            log.info("[MQ] message notify: {}", message);
            messageWebSocketServer.push(message.getReceiverId(), toJson(message));
        });
    }

    private String toJson(MessageNotifyMessage message) {
        try {
            return objectMapper.writeValueAsString(new MessageNotifyPayload(
                    message.getType(),
                    message.getContent(),
                    message.getRefId()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("消息推送序列化失败", e);
        }
    }

    @Data
    @AllArgsConstructor
    private static class MessageNotifyPayload {
        private String type;
        private String content;
        private Long refId;
    }
}