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

    /**
     * RocketMQTemplate 可能在某些环境下未配置/未启用，因此用 ObjectProvider 进行懒获取。
     * 这样即使 MQ 未启动，应用也能正常启动（发送时直接 no-op）。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * 发送视频处理消息（解析时长、补充元信息等异步任务）
     */
    @Override
    public void sendVideoProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.VIDEO_PROCESS, message);
    }

    /**
     * 发送视频封面处理消息（提取封面并上传）
     */
    @Override
    public void sendVideoCoverProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.VIDEO_COVER_PROCESS, message);
    }

    /**
     * 发送弹幕处理消息（过滤/审核/统计等）
     */
    @Override
    public void sendDanmu(DanmuMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.DANMU_PROCESS, message);
    }

    /**
     * 发送通知事件（站内信/推送/邮件等）
     */
    @Override
    public void sendNotify(NotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.NOTIFY_EVENT, message);
    }

    /**
     * 发送搜索同步消息（用于异步更新 ES/倒排索引等）
     */
    @Override
    public void sendSearchSync(SearchSyncMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.SEARCH_SYNC, message);
    }

    /**
     * 发送视频删除消息（异步删除对象存储资源、清理缓存等）
     */
    @Override
    public void sendVideoDelete(VideoDeleteMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.VIDEO_DELETE, message);
    }

    /**
     * 发送站内消息通知（WebSocket 推送/离线通知等）
     */
    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        template.convertAndSend(MqTopics.MESSAGE_NOTIFY, message);
    }
}
