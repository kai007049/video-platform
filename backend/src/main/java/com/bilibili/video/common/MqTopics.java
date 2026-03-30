package com.bilibili.video.common;

public class MqTopics {

    /** 视频处理（时长解析等） */
    public static final String VIDEO_PROCESS = "video_process";
    /** 视频封面处理 */
    public static final String VIDEO_COVER_PROCESS = "video_cover_process";
    /** 视频统计刷盘 */
    public static final String VIDEO_STATS_FLUSH = "video_stats_flush";
    /** 弹幕处理 */
    public static final String DANMU_PROCESS = "danmu_process";
    /** 通知事件（站内信/推送等） */
    public static final String NOTIFY_EVENT = "notify_event";
    /** 搜索索引同步 */
    public static final String SEARCH_SYNC = "search_sync";
    /** 删除视频资源 */
    public static final String VIDEO_DELETE = "video_delete";
    /** 站内消息通知（私信/系统通知） */
    public static final String MESSAGE_NOTIFY = "message_notify";

    private MqTopics() {}
}
