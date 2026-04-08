package com.bilibili.video.service;

import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.MessageNotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;

public interface MQService {

    /** 发送视频处理消息（解析时长等） */
    void sendVideoProcess(VideoProcessMessage message);

    /** 发送视频封面处理消息 */
    void sendVideoCoverProcess(VideoProcessMessage message);

    /** 发送弹幕处理消息 */
    void sendDanmu(DanmuMessage message);

    /** 发送通知事件消息（站内信/推送） */
    void sendNotify(NotifyMessage message);

    /** 发送搜索索引同步消息 */
    void sendSearchSync(SearchSyncMessage message);

    /** 发送视频删除消息 */
    void sendVideoDelete(VideoDeleteMessage message);

    /** 发送站内消息通知（私信/系统通知） */
    void sendMessageNotify(MessageNotifyMessage message);
}
