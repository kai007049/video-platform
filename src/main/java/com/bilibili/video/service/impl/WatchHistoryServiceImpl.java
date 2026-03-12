package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private final WatchHistoryMapper watchHistoryMapper;

    @Override
    public void saveProgress(Long userId, Long videoId, int watchSeconds) {
        if (userId == null) return;
        WatchHistory existing = watchHistoryMapper.selectOne(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .eq(WatchHistory::getVideoId, videoId));
        if (existing != null) {
            existing.setWatchSeconds(watchSeconds);
            watchHistoryMapper.updateById(existing);
        } else {
            WatchHistory h = new WatchHistory();
            h.setUserId(userId);
            h.setVideoId(videoId);
            h.setWatchSeconds(watchSeconds);
            watchHistoryMapper.insert(h);
        }
    }

    @Override
    public Integer getLastWatchSeconds(Long userId, Long videoId) {
        if (userId == null) return null;
        WatchHistory h = watchHistoryMapper.selectOne(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .eq(WatchHistory::getVideoId, videoId));
        return h != null ? h.getWatchSeconds() : null;
    }

    @Override
    public void recordWatch(Long userId, Long videoId, int watchSeconds) {
        saveProgress(userId, videoId, watchSeconds);
    }
}
