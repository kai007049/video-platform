package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.service.impl.VideoCoverProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.VIDEO_COVER_PROCESS, consumerGroup = "video-cover-process-consumer")
public class VideoCoverProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    private final VideoCoverProcessService videoCoverProcessService;

    @Override
    public void onMessage(VideoProcessMessage message) {
        log.info("[MQ] video cover process: {}", message);
        videoCoverProcessService.processByVideoId(message.getVideoId());
    }
}
