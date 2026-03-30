package com.bilibili.video.service;

/**
 * 关注服务
 */
public interface FollowService {

    void follow(Long followerId, Long followingId);
    void unfollow(Long followerId, Long followingId);
    boolean isFollowing(Long followerId, Long followingId);
    long getFollowingCount(Long userId);
    long getFanCount(Long userId);

    /**
     * 某个用户的关注列表
     *
     * @param userId       要查看的用户 ID
     * @param currentUserId 当前登录用户 ID（用于判断是否已关注），可为空
     */
    java.util.List<com.bilibili.video.model.vo.FollowUserVO> getFollowingList(Long userId, Long currentUserId);

    /**
     * 某个用户的粉丝列表
     *
     * @param userId       要查看的用户 ID
     * @param currentUserId 当前登录用户 ID（用于判断是否已关注），可为空
     */
    java.util.List<com.bilibili.video.model.vo.FollowUserVO> getFanList(Long userId, Long currentUserId);
}

