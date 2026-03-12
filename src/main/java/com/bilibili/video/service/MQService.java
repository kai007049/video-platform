package com.bilibili.video.service;

import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;

public interface MQService {

    void sendVideoProcess(VideoProcessMessage message);

    void sendDanmu(DanmuMessage message);

    void sendNotify(NotifyMessage message);

    void sendSearchSync(SearchSyncMessage message);

    void sendVideoDelete(VideoDeleteMessage message);

    void sendMessageNotify(MessageNotifyMessage message);
}
