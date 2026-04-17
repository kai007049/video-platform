package com.bilibili.video.common;

import lombok.Data;


@Data
public class Constants {
    public static final String loginTokenPrefix="login:token";//Redis登录令牌前缀
    public static final long LOGIN_EXPIRE_MINUTES = 7200;//登录过期时间
    public static final String CAPTCHA_PREFIX = "login:captcha:";
    public static final long CAPTCHA_EXPIRE_SECONDS = 300;

    /** 视频热度排行缓存 key 前缀 */
    public static final String HOT_RANK_PREFIX = "video:hot:";
    /** 热度排行窗口时长（小时） */
    public static final int HOT_WINDOW_HOURS = 24;
    /** 播放量热度排行权重 */
    public static final double HOT_WEIGHT_PLAY = 1.0;
    /** 点赞数热度排行权重 */
    public static final double HOT_WEIGHT_LIKE = 5.0;
    /** 评论数热度排行权重 */
    public static final double HOT_WEIGHT_COMMENT = 8.0;
    /** 收藏数热度排行权重 */
    public static final double HOT_WEIGHT_FAVORITE = 6.0;
    /** 本地 Caffeine 仅缓存热榜前 N 视频详情 */
    public static final int LOCAL_CACHE_HOT_VIDEO_TOP_N = 500;
}
