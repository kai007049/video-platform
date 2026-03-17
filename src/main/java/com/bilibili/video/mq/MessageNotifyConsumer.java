package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.ws.MessageWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.MESSAGE_NOTIFY, consumerGroup = "message-notify-consumer")
public class MessageNotifyConsumer implements RocketMQListener<MessageNotifyMessage> {

    private final MessageWebSocketServer messageWebSocketServer;

    @Override
    public void onMessage(MessageNotifyMessage message) {
        log.info("[MQ] message notify: {}", message);
        // 推送到 WebSocket，通知在线用户
        messageWebSocketServer.push(message.getReceiverId(), toJson(message));
    }

    /**
     * 简单拼接 JSON 字符串，便于前端解析
     * 注意：这里未做转义处理，若 content 含引号可改为 JSON 序列化工具
     */
    private String toJson(MessageNotifyMessage message) {
        return String.format("{\"type\":\"%s\",\"content\":\"%s\",\"refId\":%d}",
                message.getType(),
                message.getContent(),
                message.getRefId());
    }
}
