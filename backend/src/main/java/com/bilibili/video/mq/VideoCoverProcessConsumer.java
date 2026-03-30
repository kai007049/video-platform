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
@RocketMQMessageListener(topic = MqTopics.VIDEO_COVER_PROCESS, consumerGroup = "video-cover-process-consumer")
public class VideoCoverProcessConsumer implements RocketMQListener<VideoProcessMessage> {

    private final VideoMapper videoMapper;
    private final VideoCoverExtractor videoCoverExtractor;
    private final VideoCacheService videoCacheService;

    @Override
    public void onMessage(VideoProcessMessage message) {
        log.info("[MQ] 视频封面处理: {}", message);
        Video video = videoMapper.selectById(message.getVideoId());
        if (video == null) {
            log.warn("[MQ] 视频不存在: {}", message.getVideoId());
            return;
        }
        // 已有封面时无需重复处理
        if (video.getCoverUrl() != null && !video.getCoverUrl().isBlank()) {
            return;
        }
        try {
            // 从视频中抽取封面并上传对象存储
            String coverUrl = videoCoverExtractor.extractAndUploadCover(video.getVideoUrl());
            if (coverUrl != null) {
                // 仅更新封面字段，避免覆盖其他异步更新（如 durationSeconds）
                videoMapper.update(
                        null,
                        new LambdaUpdateWrapper<Video>()
                                .set(Video::getCoverUrl, coverUrl)
                                .eq(Video::getId, video.getId())
                );
                videoCacheService.invalidateVideo(video.getId());
                log.info("[MQ] 封面生成完成: videoId={}", video.getId());
            }
        } catch (Exception e) {
            log.warn("[MQ] 生成封面失败: {}", message.getVideoId(), e);
        }
    }
}
