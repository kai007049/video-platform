package com.kai.videoplatform.mq;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.model.mq.VideoProcessMessage;
import com.kai.videoplatform.service.impl.MqReliabilityService;
import com.kai.videoplatform.service.impl.VideoCoverProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.VIDEO_COVER_PROCESS,
        consumerGroup = "video-cover-process-consumer",
        maxReconsumeTimes = 5
)
public class VideoCoverProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    private final VideoCoverProcessService videoCoverProcessService;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(VideoProcessMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.VIDEO_COVER_PROCESS, "video-cover-process-consumer", message, () -> {
            log.info("[MQ] video cover process: {}", message);
            videoCoverProcessService.processByVideoId(message.getVideoId());
        });
    }
}