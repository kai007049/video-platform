package com.bilibili.video.common;

import lombok.Data;


@Data
public class Constants {
    public static final String loginTokenPrefix="login:token";//Redis登录令牌前缀
    public static final long LOGIN_EXPIRE_MINUTES = 7200;//登录过期时间
    public static final String CAPTCHA_PREFIX = "login:captcha:";
    public static final long CAPTCHA_EXPIRE_SECONDS = 300;

    public static final String HOT_RANK_PREFIX = "video:hot:";
    public static final int HOT_WINDOW_HOURS = 24;
    public static final double HOT_WEIGHT_PLAY = 1.0;
    public static final double HOT_WEIGHT_LIKE = 5.0;
    public static final double HOT_WEIGHT_COMMENT = 8.0;
    public static final double HOT_WEIGHT_FAVORITE = 6.0;
}
