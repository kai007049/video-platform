package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.common.Constants;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.service.VideoCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCacheServiceImpl implements VideoCacheService {

    /** Redis 视频基础详情缓存 key 前缀：video:base:v2:{videoId} */
    private static final String REDIS_BASE_KEY_PREFIX = "video:base:v2:";
    /** Redis 视频统计缓存 key 前缀：video:stat:{videoId} */
    private static final String REDIS_STATS_KEY_PREFIX = RedisConstants.VIDEO_STATS_KEY_PREFIX;
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
    private static final List<Object> VIDEO_STATS_FIELDS = List.of(
            RedisConstants.VIDEO_STAT_PLAY,
            RedisConstants.VIDEO_STAT_LIKE,
            RedisConstants.VIDEO_STAT_SAVE
    );

    private final Cache<String, Object> localVideoCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final java.util.concurrent.ExecutorService cacheDelayExecutor;

    /**
     * 从缓存中获取视频详情
     */
    @Override
    public VideoVO getVideoFromCache(Long videoId) {
        //  本地缓存
        String localKey = LOCAL_KEY_PREFIX + videoId;
        Object localCached = localVideoCache.getIfPresent(localKey);
        if (localCached instanceof VideoBaseCache base) {
            return mergeVideoVO(base, loadStats(videoId));
        }
        // Redis缓存
        String baseKey = buildBaseKey(videoId);
        Object baseCached = redisTemplate.opsForValue().get(baseKey);
        if (baseCached == null) { // 未缓存
            return null;
        }
        if (NULL_PLACEHOLDER.equals(baseCached)) {// 空值缓存
            return null;
        }
        if (!(baseCached instanceof VideoBaseCache base)) { // 非缓存对象
            return null;
        }

        if (isLocalCacheEligible(videoId)) { // 本地缓存有效
            localVideoCache.put(localKey, base);
        }
        return mergeVideoVO(base, loadStats(videoId));
    }

    /**
     * 设置缓存
     */
    @Override
    public void setVideoCache(Long videoId, VideoVO videoVO) {
        if (videoVO == null) {
            redisTemplate.opsForValue().set(buildBaseKey(videoId), NULL_PLACEHOLDER, NULL_TTL);
            return;
        }

        String localKey = LOCAL_KEY_PREFIX + videoId;
        String baseKey = buildBaseKey(videoId);
        VideoBaseCache baseCache = toBaseCache(videoVO);

        if (isLocalCacheEligible(videoId)) {
            localVideoCache.put(localKey, baseCache);
        }

        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, REDIS_TTL_JITTER.toSeconds() + 1);
        Duration ttl = REDIS_TTL.plusSeconds(jitterSeconds);
        redisTemplate.opsForValue().set(baseKey, baseCache, ttl);
    }

    /**
     * 删除缓存
     */
    @Override
    public void evictVideoCache(Long videoId) {
        localVideoCache.invalidate(LOCAL_KEY_PREFIX + videoId);
        redisTemplate.delete(buildBaseKey(videoId));
    }

    /**
     * 删除缓存（双删）
     */
    @Override
    public void doubleDeleteVideoCache(Long videoId) {
        evictVideoCache(videoId);
        cacheDelayExecutor.submit(() -> {
            try {
                Thread.sleep(500);
                redisTemplate.delete(buildBaseKey(videoId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 获取视频详情（带缓存穿透）
     */
    @Override
    public VideoVO getOrLoadVideo(Long videoId, java.util.function.Supplier<VideoVO> loader) {
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
                    return null;
                }
                setVideoCache(videoId, loaded);
                return getVideoFromCache(videoId);
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

    private VideoStatsCache loadStats(Long videoId) {
        List<Object> values = redisTemplate.opsForHash().multiGet(buildStatsKey(videoId), VIDEO_STATS_FIELDS);
        if (values == null) {
            values = Collections.emptyList();
        }
        long playDelta = values.size() > 0 ? toLong(values.get(0)) : 0L;
        long likeDelta = values.size() > 1 ? toLong(values.get(1)) : 0L;
        long saveDelta = values.size() > 2 ? toLong(values.get(2)) : 0L;
        return new VideoStatsCache(playDelta, likeDelta, saveDelta);
    }

    private VideoVO mergeVideoVO(VideoBaseCache base, VideoStatsCache stats) {
        VideoVO vo = new VideoVO();
        vo.setId(base.getId());
        vo.setTitle(base.getTitle());
        vo.setDescription(base.getDescription());
        vo.setAuthorId(base.getAuthorId());
        vo.setAuthorName(base.getAuthorName());
        vo.setAuthorAvatar(base.getAuthorAvatar());
        vo.setCoverUrl(base.getCoverUrl());
        vo.setPreviewUrl(base.getPreviewUrl());
        vo.setVideoUrl(base.getVideoUrl());
        vo.setDurationSeconds(base.getDurationSeconds());
        vo.setIsRecommended(base.getIsRecommended());
        vo.setCategoryId(base.getCategoryId());
        vo.setCreateTime(base.getCreateTime());
        vo.setCommentCount(defaultLong(base.getCommentCount()));
        vo.setPlayUrl("/api/video/" + base.getId() + "/stream");
        vo.setPlayCount(defaultLong(base.getPlayCount()) + stats.playDelta());
        vo.setLikeCount(defaultLong(base.getLikeCount()) + stats.likeDelta());
        vo.setSaveCount(defaultLong(base.getSaveCount()) + stats.saveDelta());
        return vo;
    }

    private VideoBaseCache toBaseCache(VideoVO videoVO) {
        VideoBaseCache base = new VideoBaseCache();
        base.setId(videoVO.getId());
        base.setTitle(videoVO.getTitle());
        base.setDescription(videoVO.getDescription());
        base.setAuthorId(videoVO.getAuthorId());
        base.setAuthorName(videoVO.getAuthorName());
        base.setAuthorAvatar(videoVO.getAuthorAvatar());
        base.setCoverUrl(videoVO.getCoverUrl());
        base.setPreviewUrl(videoVO.getPreviewUrl());
        base.setVideoUrl(videoVO.getVideoUrl());
        base.setDurationSeconds(videoVO.getDurationSeconds());
        base.setIsRecommended(videoVO.getIsRecommended());
        base.setCategoryId(videoVO.getCategoryId());
        base.setCreateTime(videoVO.getCreateTime());
        base.setCommentCount(videoVO.getCommentCount() == null ? 0L : videoVO.getCommentCount());
        base.setPlayCount(videoVO.getPlayCount() == null ? 0L : videoVO.getPlayCount());
        base.setLikeCount(videoVO.getLikeCount() == null ? 0L : videoVO.getLikeCount());
        base.setSaveCount(videoVO.getSaveCount() == null ? 0L : videoVO.getSaveCount());
        return base;
    }

    private boolean isLocalCacheEligible(Long videoId) {
        if (videoId == null) {
            return false;
        }
        Long rank = redisTemplate.opsForZSet().reverseRank(buildHotRankKey(), String.valueOf(videoId));
        return rank != null && rank < Constants.LOCAL_CACHE_HOT_VIDEO_TOP_N;
    }

    private String buildBaseKey(Long videoId) {
        return REDIS_BASE_KEY_PREFIX + videoId;
    }

    private String buildStatsKey(Long videoId) {
        return REDIS_STATS_KEY_PREFIX + videoId;
    }

    private String buildHotRankKey() {
        return Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    @Data
    public static class VideoBaseCache {
        private Long id;
        private String title;
        private String description;
        private Long authorId;
        private String authorName;
        private String authorAvatar;
        private String coverUrl;
        private String previewUrl;
        private String videoUrl;
        private Integer durationSeconds;
        private Boolean isRecommended;
        private Long categoryId;
        private LocalDateTime createTime;
        private Long commentCount;
        private Long playCount;
        private Long likeCount;
        private Long saveCount;
    }

    public record VideoStatsCache(long playDelta, long likeDelta, long saveDelta) {
    }
}
