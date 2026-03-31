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
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MQServiceImpl implements MQService {

    private static final String LOG_TEMPLATE = "[MQ] {} 消息发送失败，topic={}, payload={}";

    @PostConstruct
    public void logRocketMQTemplateStatus() {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("[MQ] RocketMQTemplate 未注入，消息将被跳过。请检查 RocketMQ Starter 及配置。");
        } else {
            log.info("[MQ] RocketMQTemplate 已注入，MQ 功能可用。");
        }
    }

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
        sendSafely(template, MqTopics.VIDEO_PROCESS, message, "视频处理");
    }

    /**
     * 发送视频封面处理消息（提取封面并上传）
     */
    @Override
    public void sendVideoCoverProcess(VideoProcessMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.VIDEO_COVER_PROCESS, message, "视频封面处理");
    }

    /**
     * 发送弹幕处理消息（过滤/审核/统计等）
     */
    @Override
    public void sendDanmu(DanmuMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.DANMU_PROCESS, message, "弹幕处理");
    }

    /**
     * 发送通知事件（站内信/推送/邮件等）
     */
    @Override
    public void sendNotify(NotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.NOTIFY_EVENT, message, "通知事件");
    }

    /**
     * 发送搜索同步消息（用于异步更新 ES/倒排索引等）
     */
    @Override
    public void sendSearchSync(SearchSyncMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.SEARCH_SYNC, message, "搜索同步");
    }

    /**
     * 发送视频删除消息（异步删除对象存储资源、清理缓存等）
     */
    @Override
    public void sendVideoDelete(VideoDeleteMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.VIDEO_DELETE, message, "视频删除");
    }

    /**
     * 发送站内消息通知（WebSocket 推送/离线通知等）
     */
    @Override
    public void sendMessageNotify(MessageNotifyMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) return;
        sendSafely(template, MqTopics.MESSAGE_NOTIFY, message, "站内消息通知");
    }

    /**
     * MQ 在本项目中主要承担异步增强能力，不应因为消息发送失败而直接打断核心业务。
     */
    private void sendSafely(RocketMQTemplate template, String topic, Object payload, String scene) {
        try {
            template.convertAndSend(topic, payload);
        } catch (Exception e) {
            log.error(LOG_TEMPLATE, scene, topic, payload, e);
        }
    }
}
