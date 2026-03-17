package com.bilibili.video.mq;

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
                // 更新时长并清理缓存（双删确保一致性）
                video.setDurationSeconds(durationSeconds);
                videoMapper.updateById(video);
                videoCacheService.evictVideoCache(video.getId());
                videoCacheService.doubleDeleteVideoCache(video.getId());
            }
        } catch (Exception e) {
            log.warn("[MQ] 计算视频时长失败: {}", message.getVideoId(), e);
        }
    }
}
