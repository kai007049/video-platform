package com.kai.videoplatform.model.vo;

import lombok.Data;

/**
 * 关注/粉丝列表中的用户信息
 */
@Data
public class FollowUserVO {

    private Long id;

    private String username;

    private String avatar;

    /**
     * 当前登录用户是否已关注该用户
     */
    private Boolean followed;
}
