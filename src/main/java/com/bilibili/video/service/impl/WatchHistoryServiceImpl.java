package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private static final String WATCH_PROGRESS_KEY_PREFIX = RedisConstants.VIDEO_WATCH_PROGRESS_KEY_PREFIX;
    private static final long WATCH_PROGRESS_EXPIRE_DAYS = RedisConstants.VIDEO_WATCH_PROGRESS_EXPIRE_DAYS;

    private final WatchHistoryMapper watchHistoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

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

        String key = WATCH_PROGRESS_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, String.valueOf(videoId), watchSeconds);
        redisTemplate.expire(key, WATCH_PROGRESS_EXPIRE_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }

    @Override
    public Integer getLastWatchSeconds(Long userId, Long videoId) {
        if (userId == null) return null;
        String key = WATCH_PROGRESS_KEY_PREFIX + userId;
        Object cached = redisTemplate.opsForHash().get(key, String.valueOf(videoId));
        if (cached != null) {
            if (cached instanceof Number) {
                return ((Number) cached).intValue();
            }
            try {
                return Integer.parseInt(cached.toString());
            } catch (NumberFormatException ignored) {
            }
        }

        WatchHistory h = watchHistoryMapper.selectOne(
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .eq(WatchHistory::getVideoId, videoId));
        if (h != null) {
            redisTemplate.opsForHash().put(key, String.valueOf(videoId), h.getWatchSeconds());
            redisTemplate.expire(key, WATCH_PROGRESS_EXPIRE_DAYS, java.util.concurrent.TimeUnit.DAYS);
            return h.getWatchSeconds();
        }
        return null;
    }

    @Override
    public void recordWatch(Long userId, Long videoId, int watchSeconds) {
        saveProgress(userId, videoId, watchSeconds);
    }
}
