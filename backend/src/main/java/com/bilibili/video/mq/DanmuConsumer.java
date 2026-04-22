package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.DanmuMessage;
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
        topic = MqTopics.DANMU_PROCESS,
        consumerGroup = "danmu-process-consumer",
        maxReconsumeTimes = 5
)
public class DanmuConsumer implements RocketMQListener<DanmuMessage> {

    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(DanmuMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.DANMU_PROCESS, "danmu-process-consumer", message, () -> {
            log.info("[MQ] danmu process: {}", message);
            // TODO: process danmu filtering/audit/statistics/notification logic.
        });
    }
}
