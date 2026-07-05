package com.kai.videoplatform.mq;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.mq.VideoDeleteMessage;
import com.kai.videoplatform.service.VideoCacheService;
import com.kai.videoplatform.service.impl.MqReliabilityService;
import com.kai.videoplatform.utils.MinioUtils;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "视频删除消息消费者")
public class VideoDeleteConsumer implements RocketMQListener<VideoDeleteMessage> {

    private final MinioUtils minioUtils;
    private final VideoCacheService videoCacheService;
    private final VideoMapper videoMapper;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(VideoDeleteMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.VIDEO_DELETE, "video-delete-consumer", message, () -> {
            log.info("[MQ] delete video resources: {}", message);
            Video latest = videoMapper.selectIncludingDeletedById(message.getVideoId());
            if (latest == null || !Boolean.TRUE.equals(latest.getDeleted())) {
                log.info("[MQ] skip resource cleanup because primary db state is not deleted, videoId={}", message.getVideoId());
                return;
            }
            try {
                minioUtils.deleteVideoByUrl(latest.getVideoUrl());
                minioUtils.deleteCoverByObjectName(latest.getCoverUrl());
                videoCacheService.invalidateVideo(message.getVideoId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
