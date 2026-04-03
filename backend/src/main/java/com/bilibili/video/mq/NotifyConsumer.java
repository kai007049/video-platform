package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.service.impl.MqReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.NOTIFY_EVENT,
        consumerGroup = "notify-event-consumer",
        maxReconsumeTimes = 5
)
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(NotifyMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.NOTIFY_EVENT, message, () -> {
            log.info("[MQ] notify event: {}", message);
            // TODO: implement site-mail/push/email dispatch.
        });
    }
}
