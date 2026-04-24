package com.kai.videoplatform.mq;

import com.kai.videoplatform.common.MqTopics;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.mq.NotifyMessage;
import com.kai.videoplatform.service.NotificationService;
import com.kai.videoplatform.service.impl.MqReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.NOTIFY_EVENT,
        consumerGroup = "notify-event-consumer",
        maxReconsumeTimes = 5
)
public class NotifyConsumer implements RocketMQListener<NotifyMessage> {

    private final NotificationService notificationService;
    private final MqReliabilityService mqReliabilityService;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;

    @Override
    public void onMessage(NotifyMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.NOTIFY_EVENT, "notify-event-consumer", message, () -> {
            if (message == null || message.getUserId() == null) {
                return;
            }
            String type = StringUtils.hasText(message.getType()) ? message.getType() : "notification";
            String content = buildContent(message);
            notificationService.sendNotification(message.getUserId(), type, content);
            log.info("[MQ] notification persisted: userId={}, type={}, targetId={}",
                    message.getUserId(), type, message.getTargetId());
        });
    }

    private String buildContent(NotifyMessage message) {
        Video video = message.getTargetId() == null ? null : videoMapper.selectById(message.getTargetId());
        String videoTitle = video != null && StringUtils.hasText(video.getTitle()) ? video.getTitle() : "你的视频";
        String actorName = resolveActorName(message.getUserId());
        String payload = StringUtils.trimWhitespace(message.getContent());

        return switch (message.getType()) {
            case "like" -> actorName + " 点赞了你的视频《" + videoTitle + "》";
            case "favorite" -> actorName + " 收藏了你的视频《" + videoTitle + "》";
            case "comment" -> actorName + " 评论了你的视频《" + videoTitle + "》：" + firstNonBlank(payload, "快去看看吧");
            case "danmu" -> actorName + " 在你的视频《" + videoTitle + "》发送了弹幕：" + firstNonBlank(payload, "快去看看吧");
            case "follow" -> actorName + " 关注了你";
            default -> StringUtils.hasText(payload) ? payload : "你收到了新的通知";
        };
    }

    private String resolveActorName(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "有用户";
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}