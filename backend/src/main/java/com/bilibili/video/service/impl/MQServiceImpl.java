package com.bilibili.video.service.impl;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.service.MQService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MQServiceImpl implements MQService {

    private static final String LOG_TEMPLATE = "[MQ] {} message send failed, topic={}, payload={}";

    /**
     * RocketMQTemplate may be unavailable in some environments, so we fetch it lazily.
     * This keeps startup resilient even when MQ is not ready yet.
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    @PostConstruct
    public void logRocketMQTemplateStatus() {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] RocketMQTemplate is unavailable, MQ sends will be skipped.");
        } else {
            log.info("[MQ] RocketMQTemplate is ready.");
        }
    }

    @Override
    public void sendVideoProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.VIDEO_PROCESS, message, "video process");
    }

    @Override
    public void sendVideoCoverProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.VIDEO_COVER_PROCESS, message, "video cover process");
    }

    @Override
    public void sendDanmu(DanmuMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.DANMU_PROCESS, message, "danmu process");
    }

    @Override
    public void sendNotify(NotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.NOTIFY_EVENT, message, "notify event");
    }

    @Override
    public void sendSearchSync(SearchSyncMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.SEARCH_SYNC, message, "search sync");
    }

    @Override
    public void sendVideoDelete(VideoDeleteMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.VIDEO_DELETE, message, "video delete");
    }

    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        sendAsyncSafely(template, MqTopics.MESSAGE_NOTIFY, message, "message notify");
    }

    /**
     * Search sync is non-critical for the request path, so use async send to avoid blocking user requests.
     */
    private void sendAsyncSafely(RocketMQTemplate template, String topic, Object payload, String scene) {
        try {
            template.asyncSend(topic, payload, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    if (log.isDebugEnabled()) {
                        log.debug("[MQ] {} async send success, topic={}, payload={}, result={}",
                                scene, topic, payload, sendResult);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    log.error(LOG_TEMPLATE, scene, topic, payload, e);
                }
            });
        } catch (Exception e) {
            log.error(LOG_TEMPLATE, scene, topic, payload, e);
        }
    }
}
