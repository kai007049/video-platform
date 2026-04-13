package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.service.NotificationService;
import com.bilibili.video.service.impl.MqReliabilityService;
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

    @Override
    public void onMessage(NotifyMessage message) {
        mqReliabilityService.consumeWithIdempotency(MqTopics.NOTIFY_EVENT, message, () -> {
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
        String content = StringUtils.trimWhitespace(message.getContent());
        Long targetId = message.getTargetId();
        String suffix = targetId == null ? "" : "（对象ID=" + targetId + "）";
        if (StringUtils.hasText(content)) {
            return content + suffix;
        }
        return switch (message.getType()) {
            case "like" -> "你收到了新的点赞" + suffix;
            case "favorite" -> "你收到了新的收藏" + suffix;
            case "comment" -> "你收到了新的评论" + suffix;
            case "danmu" -> "你收到了新的弹幕" + suffix;
            default -> "你收到了新的通知" + suffix;
        };
    }
}
