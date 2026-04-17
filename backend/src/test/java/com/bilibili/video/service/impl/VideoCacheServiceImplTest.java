package com.bilibili.video.service.impl;

import com.bilibili.video.model.vo.VideoVO;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoCacheServiceImplTest {

    private Cache<String, Object> localVideoCache;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private HashOperations<String, Object, Object> hashOperations;
    private ZSetOperations<String, Object> zSetOperations;
    private ExecutorService cacheDelayExecutor;
    private VideoCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        localVideoCache = mock(Cache.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        cacheDelayExecutor = mock(ExecutorService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service = new VideoCacheServiceImpl(
                localVideoCache,
                redisTemplate,
                cacheDelayExecutor
        );
    }

    @Test
    void getVideoFromCache_shouldBackfillLocalBaseCacheWhenVideoIsHot() {
        VideoCacheServiceImpl.VideoBaseCache base = createBase(123L);
        when(localVideoCache.getIfPresent("video:123")).thenReturn(null);
        when(valueOperations.get("video:base:123")).thenReturn(base);
        when(hashOperations.multiGet("video:stat:123", List.of("play", "like", "save")))
                .thenReturn(List.of(11L, 3L, 2L));
        when(zSetOperations.reverseRank("video:hot:24h", "123")).thenReturn(10L);

        VideoVO result = service.getVideoFromCache(123L);

        assertThat(result.getId()).isEqualTo(123L);
        assertThat(result.getPlayCount()).isEqualTo(111L);
        assertThat(result.getLikeCount()).isEqualTo(13L);
        assertThat(result.getSaveCount()).isEqualTo(7L);
        assertThat(result.getCommentCount()).isEqualTo(6L);
        assertThat(result.getAuthorName()).isEqualTo("admin");
        verify(localVideoCache).put("video:123", base);
    }

    @Test
    void getVideoFromCache_shouldReadFromLocalBaseCache() {
        VideoCacheServiceImpl.VideoBaseCache base = createBase(123L);
        when(localVideoCache.getIfPresent("video:123")).thenReturn(base);
        when(hashOperations.multiGet("video:stat:123", List.of("play", "like", "save")))
                .thenReturn(List.of(1L, 2L, 3L));

        VideoVO result = service.getVideoFromCache(123L);

        assertThat(result.getPlayCount()).isEqualTo(101L);
        assertThat(result.getLikeCount()).isEqualTo(12L);
        assertThat(result.getSaveCount()).isEqualTo(8L);
        verify(valueOperations, never()).get("video:base:123");
    }

    @Test
    void getVideoFromCache_shouldNotBackfillLocalCacheWhenVideoIsNotHot() {
        VideoCacheServiceImpl.VideoBaseCache base = createBase(123L);
        when(localVideoCache.getIfPresent("video:123")).thenReturn(null);
        when(valueOperations.get("video:base:123")).thenReturn(base);
        when(hashOperations.multiGet("video:stat:123", List.of("play", "like", "save")))
                .thenReturn(List.of(11L, 3L, 2L));
        when(zSetOperations.reverseRank("video:hot:24h", "123")).thenReturn(800L);

        VideoVO result = service.getVideoFromCache(123L);

        assertThat(result.getId()).isEqualTo(123L);
        verify(localVideoCache, never()).put(any(), any());
    }

    @Test
    void setVideoCache_shouldWriteBaseAndLocalBaseCacheWhenVideoIsHot() {
        VideoVO video = createVideoVO(123L);
        when(zSetOperations.reverseRank("video:hot:24h", "123")).thenReturn(20L);

        service.setVideoCache(123L, video);

        verify(localVideoCache).put(eq("video:123"), any(VideoCacheServiceImpl.VideoBaseCache.class));
        verify(valueOperations).set(eq("video:base:123"), any(VideoCacheServiceImpl.VideoBaseCache.class), any(Duration.class));
    }

    @Test
    void setVideoCache_shouldOnlyWriteBaseWhenVideoIsNotHot() {
        VideoVO video = createVideoVO(123L);
        when(zSetOperations.reverseRank("video:hot:24h", "123")).thenReturn(999L);

        service.setVideoCache(123L, video);

        verify(localVideoCache, never()).put(any(), any());
        verify(valueOperations).set(eq("video:base:123"), any(VideoCacheServiceImpl.VideoBaseCache.class), any(Duration.class));
    }

    @Test
    void setVideoCache_shouldOnlyWriteNullPlaceholderToBaseWhenVideoIsNull() {
        service.setVideoCache(123L, null);

        verify(valueOperations).set("video:base:123", "__NULL__", Duration.ofSeconds(60));
        verify(localVideoCache, never()).put(any(), any());
    }

    @Test
    void invalidateVideo_shouldEvictBaseRedisAndLocalCacheAndScheduleSecondDelete() {
        service.invalidateVideo(123L);

        verify(localVideoCache).invalidate("video:123");
        verify(redisTemplate).delete("video:base:123");
        verify(redisTemplate, never()).delete("video:stat:123");
        verify(cacheDelayExecutor).submit(any(Runnable.class));
    }

    private VideoCacheServiceImpl.VideoBaseCache createBase(Long id) {
        VideoCacheServiceImpl.VideoBaseCache base = new VideoCacheServiceImpl.VideoBaseCache();
        base.setId(id);
        base.setTitle("title");
        base.setDescription("desc");
        base.setAuthorId(1L);
        base.setAuthorName("admin");
        base.setAuthorAvatar("avatar");
        base.setCoverUrl("cover");
        base.setPreviewUrl("preview");
        base.setVideoUrl("video");
        base.setDurationSeconds(120);
        base.setIsRecommended(true);
        base.setCategoryId(9L);
        base.setCreateTime(LocalDateTime.now());
        base.setCommentCount(6L);
        base.setPlayCount(100L);
        base.setLikeCount(10L);
        base.setSaveCount(5L);
        return base;
    }

    private VideoVO createVideoVO(Long id) {
        VideoVO video = new VideoVO();
        video.setId(id);
        video.setTitle("title");
        video.setDescription("desc");
        video.setAuthorId(1L);
        video.setAuthorName("admin");
        video.setAuthorAvatar("avatar");
        video.setCoverUrl("cover");
        video.setPreviewUrl("preview");
        video.setVideoUrl("video");
        video.setDurationSeconds(120);
        video.setIsRecommended(true);
        video.setCategoryId(9L);
        video.setCreateTime(LocalDateTime.now());
        video.setCommentCount(6L);
        video.setPlayCount(100L);
        video.setLikeCount(10L);
        video.setSaveCount(5L);
        return video;
    }
}
