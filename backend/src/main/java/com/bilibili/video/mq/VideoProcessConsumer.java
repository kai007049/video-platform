package com.bilibili.video.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bilibili.video.common.MqTopics;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.service.impl.MqReliabilityService;
import com.bilibili.video.utils.VideoCoverExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.VIDEO_PROCESS,
        consumerGroup = "video-process-consumer",
        maxReconsumeTimes = 5
)
public class VideoProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    private final VideoMapper videoMapper;
    private final VideoCoverExtractor videoCoverExtractor;
    private final VideoCacheService videoCacheService;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(VideoProcessMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.VIDEO_PROCESS, message, () -> {
            log.info("[MQ] video process: {}", message);
            Video video = videoMapper.selectById(message.getVideoId());
            if (video == null) {
                log.warn("[MQ] video not found: {}", message.getVideoId());
                return;
            }

            Integer durationSeconds = videoCoverExtractor.extractDurationSeconds(video.getVideoUrl());
            if (durationSeconds == null || durationSeconds <= 0) {
                throw new RuntimeException("duration extraction failed, videoId=" + message.getVideoId());
            }

            int updated = videoMapper.update(
                    null,
                    new LambdaUpdateWrapper<Video>()
                            .set(Video::getDurationSeconds, durationSeconds)
                            .eq(Video::getId, video.getId())
                            .and(w -> w.isNull(Video::getDurationSeconds).or().eq(Video::getDurationSeconds, 0))
            );
            if (updated > 0) {
                videoCacheService.invalidateVideo(video.getId());
                log.info("[MQ] video duration updated: videoId={}, duration={}", video.getId(), durationSeconds);
            } else {
                log.info("[MQ] skip duration update because value already initialized: videoId={}", video.getId());
            }
        });
    }
}
