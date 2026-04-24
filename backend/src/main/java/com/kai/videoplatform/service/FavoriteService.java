package com.kai.videoplatform.service;

/**
 * 收藏服务
 */
public interface FavoriteService {

    void add(Long userId, Long videoId);

    void remove(Long userId, Long videoId);

    boolean isFavorited(Long userId, Long videoId);
}