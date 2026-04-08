package com.bilibili.video.mq;

import com.bilibili.video.client.AgentClient;
import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.VideoSemanticIndexMessage;
import com.bilibili.video.service.impl.MqReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.VIDEO_SEMANTIC_INDEX,
        consumerGroup = "video-semantic-index-consumer",
        maxReconsumeTimes = 5
)
public class VideoSemanticIndexConsumer implements RocketMQListener<VideoSemanticIndexMessage> {

    private final AgentClient agentClient;
    private final MqReliabilityService mqReliabilityService;

    @Override
    public void onMessage(VideoSemanticIndexMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.VIDEO_SEMANTIC_INDEX, message, () -> {
            log.info("[MQ] video semantic index: {}", message);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("video_id", message.getVideoId());
            payload.put("video_url", message.getVideoUrl());
            payload.put("cover_url", message.getCoverUrl());
            payload.put("title", message.getTitle());
            payload.put("description", message.getDescription());
            payload.put("category_id", message.getCategoryId());
            payload.put("tags", message.getTags());
            agentClient.indexVideoSemantic(payload);
        });
    }
}
