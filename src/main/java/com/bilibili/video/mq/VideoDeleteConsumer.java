package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.VIDEO_DELETE, consumerGroup = "video-delete-consumer")
public class VideoDeleteConsumer implements RocketMQListener<VideoDeleteMessage> {

    private final MinioUtils minioUtils;
    private final VideoCacheService videoCacheService;

    @Override
    public void onMessage(VideoDeleteMessage message) {
        log.info("[MQ] 删除视频资源: {}", message);
        try {
            // 删除对象存储中的视频与封面资源
            minioUtils.deleteVideoByUrl(message.getVideoUrl());
            minioUtils.deleteCoverByObjectName(message.getCoverObject());
            // 清理缓存，避免前端继续读到已删除的数据
            videoCacheService.evictVideoCache(message.getVideoId());
        } catch (Exception e) {
            log.error("[MQ] 删除资源失败: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
