package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.DanmuMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqTopics.DANMU_PROCESS, consumerGroup = "danmu-process-consumer")
public class DanmuConsumer implements RocketMQListener<DanmuMessage> {

    @Override
    public void onMessage(DanmuMessage message) {
        log.info("[MQ] 弹幕处理: {}", message);
        // TODO: 在此异步处理弹幕过滤/审核/统计/通知等逻辑
        // 例如：关键词过滤 -> 违规入库 -> 弹幕计数 -> 用户消息通知
    }
}
