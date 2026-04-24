package com.kai.videoplatform.service;

/**
 * 点赞服务接口
 */
public interface LikeService {

    void like(Long videoId, Long userId);

    void unlike(Long videoId, Long userId);

    boolean isLiked(Long videoId, Long userId);
}