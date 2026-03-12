package com.bilibili.video.task;

import com.bilibili.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 播放量同步任务 - Redis -> MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayCountSyncTask {

    private static final String PLAY_COUNT_KEY = "video:play_count:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoMapper videoMapper;

    @Scheduled(cron = "${play-count.sync-cron:0 * * * * ?}")
    public void syncPlayCount() {
        Set<String> keys = redisTemplate.keys(PLAY_COUNT_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                Object countObj = redisTemplate.opsForHash().get(key, "count");
                if (countObj == null) {
                    continue;
                }
                long count = ((Number) countObj).longValue();
                if (count <= 0) {
                    continue;
                }
                String videoIdStr = key.replace(PLAY_COUNT_KEY, "");
                Long videoId = Long.parseLong(videoIdStr);
                videoMapper.incrementPlayCount(videoId, count);
                redisTemplate.opsForHash().delete(key, "count");
                log.debug("Synced play count for video {}: +{}", videoId, count);
            } catch (Exception e) {
                log.warn("Failed to sync play count for key {}: {}", key, e.getMessage());
            }
        }
    }
}
