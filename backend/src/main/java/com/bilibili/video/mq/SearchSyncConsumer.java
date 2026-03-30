package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.SEARCH_SYNC, consumerGroup = "search-sync-consumer")
public class SearchSyncConsumer implements RocketMQListener<SearchSyncMessage> {

    private final SearchService searchService;

    @Override
    public void onMessage(SearchSyncMessage message) {
        if (message == null || message.getEntityId() == null) return;
        if (!"video".equalsIgnoreCase(message.getEntityType())) return;
        String action = message.getAction();
        if (action == null) return;
        switch (action) {
            case "create":
            case "update":
                searchService.indexVideo(message.getEntityId());
                break;
            case "delete":
                searchService.deleteVideo(message.getEntityId());
                break;
            default:
                log.warn("unknown search sync action: {}", action);
        }
    }
}
