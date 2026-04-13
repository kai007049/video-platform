package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.RecExposureLogService;
import com.bilibili.video.service.RecommendationService;
import com.bilibili.video.service.VideoCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoQueryServiceTest {

    @Test
    void listHot_shouldFallbackToDatabaseWhenRedisHotRankIsEmpty() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoCacheService videoCacheService = mock(VideoCacheService.class);
        VideoViewAssembler videoViewAssembler = mock(VideoViewAssembler.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);
        RecommendationService recommendationService = mock(RecommendationService.class);
        RecExposureLogService recExposureLogService = mock(RecExposureLogService.class);

        VideoQueryService service = new VideoQueryService(
                videoMapper,
                videoLikeMapper,
                favoriteMapper,
                watchHistoryMapper,
                videoCacheService,
                videoViewAssembler,
                redisTemplate,
                recommendationService,
                recExposureLogService
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(anyString(), any(), any())).thenReturn(Set.of());

        Video hotVideo = new Video();
        hotVideo.setId(100L);
        hotVideo.setTitle("old-hot-video");
        hotVideo.setPlayCount(999L);
        hotVideo.setCreateTime(java.time.LocalDateTime.now());

        Page<Video> fallbackPage = new Page<>(1, 12, 1);
        fallbackPage.setRecords(List.of(hotVideo));
        when(videoMapper.selectPage(any(Page.class), any())).thenReturn(fallbackPage);

        VideoVO hotVideoVO = new VideoVO();
        hotVideoVO.setId(100L);
        hotVideoVO.setTitle("old-hot-video");
        when(videoViewAssembler.toVideoVOList(List.of(hotVideo), 1L)).thenReturn(List.of(hotVideoVO));

        var result = service.listHot(1, 12, 1L);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(100L);
        verify(videoMapper).selectPage(any(Page.class), any());
        verify(recExposureLogService, never()).logExposureBatch(any(), any(), any(), any(), any());
    }
}
