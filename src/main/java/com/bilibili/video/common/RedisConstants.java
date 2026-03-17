package com.bilibili.video.common;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RedisConstants {

    private RedisConstants() {}

    /** 视频统计 Hash 的 key 前缀：video:stat:{videoId} */
    public static final String VIDEO_STATS_KEY_PREFIX = "video:stat:";
    /** 播放量字段名 */
    public static final String VIDEO_STAT_PLAY = "play";
    /** 点赞数字段名 */
    public static final String VIDEO_STAT_LIKE = "like";
    /** 收藏数字段名 */
    public static final String VIDEO_STAT_SAVE = "save";
    /** 评论数字段名 */
    public static final String VIDEO_STAT_COMMENT = "comment";
    /** 统计缓存过期天数 */
    public static final long VIDEO_STATS_EXPIRE_DAYS = 7;

    /** 用户点赞状态 key 前缀：video:like:{videoId}:{userId} */
    public static final String VIDEO_LIKE_KEY_PREFIX = "video:like:";
    /** 点赞状态过期天数 */
    public static final long VIDEO_LIKE_EXPIRE_DAYS = 7;

    /** 用户收藏状态 key 前缀：video:fav:{videoId}:{userId} */
    public static final String VIDEO_FAVORITE_KEY_PREFIX = "video:fav:";
    /** 收藏状态过期天数 */
    public static final long VIDEO_FAVORITE_EXPIRE_DAYS = 7;

    /** 观看进度 Hash key 前缀：video:watch:progress:{userId} */
    public static final String VIDEO_WATCH_PROGRESS_KEY_PREFIX = "video:watch:progress:";
    /** 观看进度过期天数 */
    public static final long VIDEO_WATCH_PROGRESS_EXPIRE_DAYS = 7;

    /** 评论列表缓存 key 前缀：video:comment:list:{videoId} */
    public static final String COMMENT_LIST_KEY_PREFIX = "video:comment:list:";
    /** 评论列表正常缓存 TTL */
    public static final Duration COMMENT_LIST_TTL = Duration.ofMinutes(2);
    /** 评论列表空值缓存 TTL（防穿透） */
    public static final Duration COMMENT_LIST_NULL_TTL = Duration.ofSeconds(30);

    /** 统计刷盘分布式锁 key */
    public static final String VIDEO_STATS_SYNC_LOCK = "lock:video:stat:sync";
    /** 统计刷盘锁过期时间（秒） */
    public static final long VIDEO_STATS_SYNC_LOCK_TTL_SECONDS = 30;

    /** 默认时间单位：天 */
    public static final TimeUnit DEFAULT_TIME_UNIT_DAYS = TimeUnit.DAYS;
}
