package com.bilibili.video.service.impl;

import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.VideoCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCacheServiceImpl implements VideoCacheService {

    private static final String REDIS_KEY_PREFIX = "video:info:";
    private static final String LOCAL_KEY_PREFIX = "video:";
    private static final String LOCK_KEY_PREFIX = "lock:video:";
    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);
    private static final Duration REDIS_TTL_JITTER = Duration.ofMinutes(5);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final Cache<String, Object> localVideoCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ExecutorService cacheDelayExecutor;

    @Override
    public VideoVO getVideoFromCache(Long videoId) {
        String localKey = LOCAL_KEY_PREFIX + videoId;
        Object localCached = localVideoCache.getIfPresent(localKey);
        if (localCached instanceof VideoVO) {
            log.info("cache hit (Caffeine): videoId={}", videoId);
            return (VideoVO) localCached;
        }

        String redisKey = REDIS_KEY_PREFIX + videoId;
        Object redisCached = redisTemplate.opsForValue().get(redisKey);
        if (redisCached != null) {
            if (NULL_PLACEHOLDER.equals(redisCached)) {
                log.info("cache penetration: videoId={}", videoId);
                return null;
            }
            VideoVO vo = objectMapper.convertValue(redisCached, VideoVO.class);
            localVideoCache.put(localKey, vo);
            log.info("cache hit (Redis): videoId={}", videoId);
            return vo;
        }

        log.info("cache miss: videoId={}", videoId);
        return null;
    }

    @Override
    public void setVideoCache(Long videoId, VideoVO videoVO) {
        if (videoVO == null) {
            // 缓存空对象，防止缓存穿透
            String redisKey = REDIS_KEY_PREFIX + videoId;
            redisTemplate.opsForValue().set(redisKey, NULL_PLACEHOLDER, NULL_TTL);
            log.info("cache penetration: videoId={}", videoId);
            return;
        }
        String localKey = LOCAL_KEY_PREFIX + videoId;
        String redisKey = REDIS_KEY_PREFIX + videoId;
        localVideoCache.put(localKey, videoVO);

        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, REDIS_TTL_JITTER.toSeconds() + 1);
        Duration ttl = REDIS_TTL.plusSeconds(jitterSeconds);
        log.info("cache snowball: videoId={}, ttlSeconds={}", videoId, ttl.toSeconds());
        redisTemplate.opsForValue().set(redisKey, videoVO, ttl);
    }

    @Override
    public void evictVideoCache(Long videoId) {
        String localKey = LOCAL_KEY_PREFIX + videoId;
        String redisKey = REDIS_KEY_PREFIX + videoId;
        localVideoCache.invalidate(localKey);
        redisTemplate.delete(redisKey);
    }


    @Override
    public void doubleDeleteVideoCache(Long videoId) {
        evictVideoCache(videoId);
        cacheDelayExecutor.submit(() -> {
            try {
                Thread.sleep(500);
                String redisKey = REDIS_KEY_PREFIX + videoId;
                redisTemplate.delete(redisKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public VideoVO getVideoWithLoader(Long videoId, java.util.function.Supplier<VideoVO> loader) {
        VideoVO cached = getVideoFromCache(videoId);
        if (cached != null) {
            return cached;
        }

        String lockKey = LOCK_KEY_PREFIX + videoId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                VideoVO loaded = loader.get();
                if (loaded == null) {
                    setVideoCache(videoId, null);
                } else {
                    setVideoCache(videoId, loaded);
                }
                return loaded;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // 缓存击穿：等待缓存回填
        log.info("cache breakdown: videoId={}", videoId);
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return getVideoFromCache(videoId);
    }
}

