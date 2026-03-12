package com.bilibili.video.mq;

import com.bilibili.video.common.MqTopics;
import com.bilibili.video.model.mq.SearchSyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = MqTopics.SEARCH_SYNC, consumerGroup = "search-sync-consumer")
public class SearchSyncConsumer implements RocketMQListener<SearchSyncMessage> {

    @Override
    public void onMessage(SearchSyncMessage message) {
        log.info("[MQ] 搜索同步(占位): {}", message);
    }
}
