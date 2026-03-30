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

    /** Redis 视频详情缓存 key 前缀：video:info:{videoId} */
    private static final String REDIS_KEY_PREFIX = "video:info:";
    /** 本地 Caffeine 缓存 key 前缀 */
    private static final String LOCAL_KEY_PREFIX = "video:";
    /** Redis 分布式锁 key 前缀，用于防止缓存击穿 */
    private static final String LOCK_KEY_PREFIX = "lock:video:";
    /** 空值占位符：用于防止缓存穿透 */
    private static final String NULL_PLACEHOLDER = "__NULL__";
    /** Redis 正常缓存 TTL */
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);
    /** Redis TTL 抖动，防止缓存雪崩 */
    private static final Duration REDIS_TTL_JITTER = Duration.ofMinutes(5);
    /** 空值缓存 TTL */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final Cache<String, Object> localVideoCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ExecutorService cacheDelayExecutor;

    /**
     * 从缓存中获取视频详情
     * @param videoId
     * @return
     */
    @Override
    public VideoVO getVideoFromCache(Long videoId) {
        // 本地缓存
        String localKey = LOCAL_KEY_PREFIX + videoId;
        Object localCached = localVideoCache.getIfPresent(localKey);
        if (localCached instanceof VideoVO) {
            return (VideoVO) localCached;
        }

        // Redis 缓存
        String redisKey = REDIS_KEY_PREFIX + videoId;
        Object redisCached = redisTemplate.opsForValue().get(redisKey);
        if (redisCached != null) {
            if (NULL_PLACEHOLDER.equals(redisCached)) {
                // 空值命中：判定为缓存穿透
                return null;
            }
            VideoVO vo = objectMapper.convertValue(redisCached, VideoVO.class);
            // 回填本地缓存，加速后续访问
            localVideoCache.put(localKey, vo);
            return vo;
        }
        return null;
    }


    /**
     * 设置缓存
     * @param videoId
     * @param videoVO
     */
    @Override
    public void setVideoCache(Long videoId, VideoVO videoVO) {
        if (videoVO == null) {
            // 缓存空对象，防止缓存穿透
            String redisKey = REDIS_KEY_PREFIX + videoId;
            redisTemplate.opsForValue().set(redisKey, NULL_PLACEHOLDER, NULL_TTL);
            return;
        }
        String localKey = LOCAL_KEY_PREFIX + videoId;
        String redisKey = REDIS_KEY_PREFIX + videoId;
        // 同步写入本地缓存
        localVideoCache.put(localKey, videoVO);

        // 通过 TTL 抖动避免同一时刻大量 key 过期造成雪崩
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, REDIS_TTL_JITTER.toSeconds() + 1);
        Duration ttl = REDIS_TTL.plusSeconds(jitterSeconds);
        redisTemplate.opsForValue().set(redisKey, videoVO, ttl);
    }

    /**
     * 删除缓存
     * @param videoId
     */
    @Override
    public void evictVideoCache(Long videoId) {
        String localKey = LOCAL_KEY_PREFIX + videoId;
        String redisKey = REDIS_KEY_PREFIX + videoId;
        // 先清本地，再清 Redis
        localVideoCache.invalidate(localKey);
        redisTemplate.delete(redisKey);
    }


    /**
     * 删除缓存（双删）
     * @param videoId
     */
    @Override
    public void doubleDeleteVideoCache(Long videoId) {
        // 双删策略：先删缓存 -> 延迟再删一次，防止并发读写导致脏数据
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

    /**
     * 获取视频详情（带缓存穿透）
     * @param videoId
     * @param loader
     * @return
     */
    @Override
    public VideoVO getOrLoadVideo(Long videoId, java.util.function.Supplier<VideoVO> loader) {
        // 先读缓存
        VideoVO cached = getVideoFromCache(videoId);
        if (cached != null) {
            return cached;
        }

        // 通过 Redis 分布式锁限制回源
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

        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return getVideoFromCache(videoId);
    }

    @Override
    public void invalidateVideo(Long videoId) {
        doubleDeleteVideoCache(videoId);
    }
}

