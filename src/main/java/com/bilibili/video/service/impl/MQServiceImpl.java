package com.bilibili.video.service.impl;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.service.MQService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MQServiceImpl implements MQService {

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * 发送视频处理消息
     * @param message
     */
    @Override
    public void sendVideoProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.VIDEO_PROCESS, message);
    }

    /**
     * 发送弹幕处理消息
     * @param message
     */
    @Override
    public void sendDanmu(DanmuMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.DANMU_PROCESS, message);
    }

    /**
     * 发送通知消息
     * @param message
     */
    @Override
    public void sendNotify(NotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.NOTIFY_EVENT, message);
    }

    /**
     * 发送搜索同步消息
     * @param message
     */
    @Override
    public void sendSearchSync(SearchSyncMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.SEARCH_SYNC, message);
    }

    /**
     * 发送视频删除消息
     * @param message
     */
    @Override
    public void sendVideoDelete(VideoDeleteMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.VIDEO_DELETE, message);
    }

    /**
     * 发送站内消息通知（私信/系统通知）
     * @param message
     */
    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.MESSAGE_NOTIFY, message);
    }
}
