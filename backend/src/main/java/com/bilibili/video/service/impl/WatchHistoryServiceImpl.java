package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
        // 先尝试按唯一键更新，避免并发场景下“先查再插”触发重复插入。
        int updated = updateExistingProgress(userId, videoId, watchSeconds);
        if (updated == 0) {
            try {
                WatchHistory history = new WatchHistory();
                history.setUserId(userId);
                history.setVideoId(videoId);
                history.setWatchSeconds(watchSeconds);
                watchHistoryMapper.insert(history);
            } catch (DuplicateKeyException ex) {
                // 并发下可能有别的请求已插入成功，这里回退为更新即可。
                updateExistingProgress(userId, videoId, watchSeconds);
            }
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

    private int updateExistingProgress(Long userId, Long videoId, int watchSeconds) {
        WatchHistory update = new WatchHistory();
        update.setWatchSeconds(watchSeconds);
        return watchHistoryMapper.update(
                update,
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .eq(WatchHistory::getVideoId, videoId)
        );
    }
}
