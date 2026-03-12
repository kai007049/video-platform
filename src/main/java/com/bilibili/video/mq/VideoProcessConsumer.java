package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.VideoProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqTopics.VIDEO_PROCESS, consumerGroup = "video-process-consumer")
public class VideoProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    @Override
    public void onMessage(VideoProcessMessage message) {
        log.info("[MQ] 视频处理: {}", message);
        // TODO: 在此异步处理转码/封面/审核等逻辑
    }
}
