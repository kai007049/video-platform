package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.NotifyMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqTopics.NOTIFY_EVENT, consumerGroup = "notify-event-consumer")
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    @Override
    public void onMessage(NotifyMessage message) {
        log.info("[MQ] 通知事件: {}", message);
        // TODO: 在此异步发送站内信/推送等逻辑
    }
}
