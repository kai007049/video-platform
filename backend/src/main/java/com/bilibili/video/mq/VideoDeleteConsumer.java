package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.service.impl.MqReliabilityService;
import com.bilibili.video.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.VIDEO_DELETE,
        consumerGroup = "video-delete-consumer",
        maxReconsumeTimes = 5
)
public class VideoDeleteConsumer implements RocketMQListener<VideoDeleteMessage> {

    private final MinioUtils minioUtils;
    private final VideoCacheService videoCacheService;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(VideoDeleteMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.VIDEO_DELETE, message, () -> {
            log.info("[MQ] delete video resources: {}", message);
            try {
                minioUtils.deleteVideoByUrl(message.getVideoUrl());
                minioUtils.deleteCoverByObjectName(message.getCoverObject());
                videoCacheService.invalidateVideo(message.getVideoId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
