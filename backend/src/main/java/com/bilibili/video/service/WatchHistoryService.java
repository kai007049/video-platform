package com.bilibili.video.service;

/**
 * 观看历史服务
 */
public interface WatchHistoryService {

    void saveProgress(Long userId, Long videoId, int watchSeconds);

    Integer getLastWatchSeconds(Long userId, Long videoId);

    void recordWatch(Long userId, Long videoId, int watchSeconds);
}
