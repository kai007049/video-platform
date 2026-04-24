package com.kai.videoplatform.task;

import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.mapper.VideoLikeMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 视频统计同步任务 - Redis -> MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayCountSyncTask {

    private static final String VIDEO_STATS_KEY = RedisConstants.VIDEO_STATS_KEY_PREFIX;

    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoMapper videoMapper;
    private final VideoLikeMapper videoLikeMapper;

    @Scheduled(cron = "${play-count.sync-cron:0 * * * * ?}")
    public void syncPlayCount() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                RedisConstants.VIDEO_STATS_SYNC_LOCK,
                "1",
                RedisConstants.VIDEO_STATS_SYNC_LOCK_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            Set<String> keys = redisTemplate.keys(VIDEO_STATS_KEY + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            for (String key : keys) {
                try {
                    String videoIdStr = key.replace(VIDEO_STATS_KEY, "");
                    Long videoId = Long.parseLong(videoIdStr);

                    Object playObj = redisTemplate.opsForHash().get(key, RedisConstants.VIDEO_STAT_PLAY);
                    Object likeObj = redisTemplate.opsForHash().get(key, RedisConstants.VIDEO_STAT_LIKE);
                    Object saveObj = redisTemplate.opsForHash().get(key, RedisConstants.VIDEO_STAT_SAVE);

                    if (playObj != null) {
                        long count = ((Number) playObj).longValue();
                        if (count != 0) {
                            videoMapper.incrementPlayCount(videoId, count);
                            redisTemplate.opsForHash().delete(key, RedisConstants.VIDEO_STAT_PLAY);
                            log.debug("Synced play count for video {}: {}", videoId, count);
                        }
                    }

                    if (likeObj != null) {
                        long delta = ((Number) likeObj).longValue();
                        if (delta != 0) {
                            videoLikeMapper.incrementLikeCount(videoId, (int) delta);
                            redisTemplate.opsForHash().delete(key, RedisConstants.VIDEO_STAT_LIKE);
                            log.debug("Synced like count for video {}: {}", videoId, delta);
                        }
                    }

                    if (saveObj != null) {
                        long delta = ((Number) saveObj).longValue();
                        if (delta != 0) {
                            videoMapper.incrementSaveCount(videoId, delta);
                            redisTemplate.opsForHash().delete(key, RedisConstants.VIDEO_STAT_SAVE);
                            log.debug("Synced save count for video {}: {}", videoId, delta);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync stats for key {}: {}", key, e.getMessage());
                }
            }
        } finally {
            redisTemplate.delete(RedisConstants.VIDEO_STATS_SYNC_LOCK);
        }
    }
}