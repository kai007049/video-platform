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

    /** 分类树缓存 key */
    public static final String CATEGORY_TREE_KEY = "category:tree";
    /** 分类列表缓存 key */
    public static final String CATEGORY_LIST_KEY = "category:list";
    /** 标签列表缓存 key */
    public static final String TAG_LIST_KEY = "tag:list";
    /** 元数据缓存 TTL */
    public static final Duration METADATA_CACHE_TTL = Duration.ofMinutes(30);

    /** 搜索历史 key 前缀：search:history:{userId} */
    public static final String SEARCH_HISTORY_KEY_PREFIX = "search:history:";
    /** 搜索历史 TTL */
    public static final Duration SEARCH_HISTORY_TTL = Duration.ofDays(30);

    /** 热搜日桶 key 前缀：search:hot:{yyyyMMdd} */
    public static final String HOT_SEARCH_KEY_PREFIX = "search:hot:";
    /** 热搜窗口天数 */
    public static final int HOT_SEARCH_WINDOW_DAYS = 7;
    /** 热搜日桶缓存保留天数 */
    public static final Duration HOT_SEARCH_BUCKET_TTL = Duration.ofDays(HOT_SEARCH_WINDOW_DAYS + 1L);
    /** 热搜聚合临时 key */
    public static final String HOT_SEARCH_WINDOW_KEY = "search:hot:window:7d";
    /** 热搜聚合临时 key TTL */
    public static final Duration HOT_SEARCH_WINDOW_TTL = Duration.ofSeconds(60);
    /** 搜索结果缓存 key 前缀：search:video:* */
    public static final String SEARCH_RESULT_KEY_PREFIX = "search:video:";
    /** 搜索结果缓存 TTL */
    public static final Duration SEARCH_RESULT_TTL = Duration.ofSeconds(60);
    /** 搜索空结果缓存 TTL */
    public static final Duration SEARCH_RESULT_EMPTY_TTL = Duration.ofSeconds(30);

    /** 推荐结果窗口缓存 key 前缀：rec:* */
    public static final String RECOMMEND_RESULT_KEY_PREFIX = "rec:";
    /** 游客推荐结果窗口 TTL */
    public static final Duration RECOMMEND_GUEST_WINDOW_TTL = Duration.ofSeconds(60);
    /** 登录用户推荐结果窗口 TTL */
    public static final Duration RECOMMEND_USER_WINDOW_TTL = Duration.ofSeconds(15);
    /** 推荐结果窗口长度 */
    public static final int RECOMMEND_RESULT_WINDOW_SIZE = 50;
    /** 推荐结果缓存重建锁前缀 */
    public static final String RECOMMEND_RESULT_LOCK_PREFIX = "lock:rec:";
    /** 推荐结果缓存重建锁 TTL */
    public static final Duration RECOMMEND_RESULT_LOCK_TTL = Duration.ofSeconds(5);

    /** 统计刷盘分布式锁 key */
    public static final String VIDEO_STATS_SYNC_LOCK = "lock:video:stat:sync";
    /** 统计刷盘锁过期时间（秒） */
    public static final long VIDEO_STATS_SYNC_LOCK_TTL_SECONDS = 30;

    /** 默认时间单位：天 */
    public static final TimeUnit DEFAULT_TIME_UNIT_DAYS = TimeUnit.DAYS;
}
