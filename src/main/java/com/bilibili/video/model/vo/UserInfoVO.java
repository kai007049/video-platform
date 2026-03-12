package com.bilibili.video.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO（不包含密码）
 */
@Data
public class UserInfoVO {

    private Long id;
    private String username;
    private String avatar;
    private Boolean isAdmin;
    private LocalDateTime createTime;
}
