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
}
