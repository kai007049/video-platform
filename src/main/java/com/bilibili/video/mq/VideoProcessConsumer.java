package com.bilibili.video.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bilibili.video.common.MqTopics;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.utils.VideoCoverExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.VIDEO_PROCESS, consumerGroup = "video-process-consumer")
public class VideoProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    private final VideoMapper videoMapper;
    private final VideoCoverExtractor videoCoverExtractor;
    private final VideoCacheService videoCacheService;

    @Override
    public void onMessage(VideoProcessMessage message) {
        log.info("[MQ] 视频处理: {}", message);
        Video video = videoMapper.selectById(message.getVideoId());
        if (video == null) {
            log.warn("[MQ] 视频不存在: {}", message.getVideoId());
            return;
        }
        try {
            // 从视频资源中解析时长
            Integer durationSeconds = videoCoverExtractor.extractDurationSeconds(video.getVideoUrl());
            log.info("[MQ] 获取视频时长: {}", durationSeconds);
            if (durationSeconds != null && durationSeconds > 0) {
                // 仅在时长仍为 0/空时更新，避免被其他异步流程覆盖
                int updated = videoMapper.update(
                        null,
                        new LambdaUpdateWrapper<Video>()
                                .set(Video::getDurationSeconds, durationSeconds)
                                .eq(Video::getId, video.getId())
                                .and(w -> w.isNull(Video::getDurationSeconds).or().eq(Video::getDurationSeconds, 0))
                );
                if (updated > 0) {
                    videoCacheService.evictVideoCache(video.getId());
                    videoCacheService.doubleDeleteVideoCache(video.getId());
                    log.info("[MQ] 更新视频时长成功: videoId={}, duration={}", video.getId(), durationSeconds);
                } else {
                    log.info("[MQ] 跳过更新时长(已存在非0时长): videoId={}", video.getId());
                }
            }
        } catch (Exception e) {
            log.warn("[MQ] 计算视频时长失败: {}", message.getVideoId(), e);
        }
    }
}
