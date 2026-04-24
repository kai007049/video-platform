package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.FavoriteMapper;
import com.kai.videoplatform.mapper.FollowMapper;
import com.kai.videoplatform.mapper.VideoLikeMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.mapper.VideoTagFeatureMapper;
import com.kai.videoplatform.mapper.WatchHistoryMapper;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.service.RecExposureLogService;
import com.kai.videoplatform.service.RecommendationFeatureService;
import com.kai.videoplatform.service.UserProfileSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

    @Test
    void shouldTreatNonEmptyExcludeIdsAsRefreshAndReturnFirstAvailableBatch() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);
        UserProfileSummaryService userProfileSummaryService = mock(UserProfileSummaryService.class);
        VideoViewAssembler assembler = mock(VideoViewAssembler.class);
        RecExposureLogService exposureLogService = mock(RecExposureLogService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        RecommendationServiceImpl service = new RecommendationServiceImpl(
                videoMapper,
                videoTagFeatureMapper,
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                recommendationFeatureService,
                userProfileSummaryService,
                assembler,
                exposureLogService,
                redisTemplate
        );

        List<Video> editorialVideos = List.of(
                video(1L, true, 100),
                video(2L, true, 99),
                video(3L, true, 98),
                video(4L, true, 97),
                video(5L, true, 96)
        );
        List<Video> freshVideos = List.of(
                video(5L, true, 96),
                video(4L, true, 97),
                video(3L, true, 98),
                video(2L, true, 99),
                video(1L, true, 100)
        );

        when(videoMapper.selectList(any())).thenReturn(freshVideos, editorialVideos);
        when(videoMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            List<Long> ids = ((java.util.Collection<Long>) invocation.getArgument(0)).stream().toList();
            return editorialVideos.stream().filter(video -> ids.contains(video.getId())).toList();
        });
        when(assembler.toVideoVOList(any(), any())).thenAnswer(invocation ->
                ((List<Video>) invocation.getArgument(0)).stream().map(video -> {
                    VideoVO vo = new VideoVO();
                    vo.setId(video.getId());
                    vo.setTitle(video.getTitle());
                    return vo;
                }).toList()
        );
        when(exposureLogService.logRecommendationExposureFromVideos(any(), anyString(), anyInt(), anyInt(), any(), any(Map.class), anyString()))
                .thenReturn("req-1");

        IPage<VideoVO> page = service.listRecommended(2, 3, 1L, Set.of(1L, 2L));

        assertThat(page.getRecords()).extracting(VideoVO::getId)
                .containsExactly(5L, 4L, 3L);
        assertThat(page.getRecords()).extracting(VideoVO::getId)
                .doesNotContain(1L, 2L)
                .hasSize(3);
        verify(exposureLogService).logRecommendationExposureFromVideos(eq(1L), eq("recommended"), eq(2), eq(3), any(), any(Map.class), anyString());
    }

    @Test
    void shouldKeepPaginationWhenExcludeIdsAreEmpty() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);
        UserProfileSummaryService userProfileSummaryService = mock(UserProfileSummaryService.class);
        VideoViewAssembler assembler = mock(VideoViewAssembler.class);
        RecExposureLogService exposureLogService = mock(RecExposureLogService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        RecommendationServiceImpl service = new RecommendationServiceImpl(
                videoMapper,
                videoTagFeatureMapper,
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                recommendationFeatureService,
                userProfileSummaryService,
                assembler,
                exposureLogService,
                redisTemplate
        );

        List<Video> editorialVideos = List.of(
                video(11L, true, 100),
                video(12L, true, 99),
                video(13L, true, 98),
                video(14L, true, 97)
        );
        when(videoMapper.selectList(any())).thenReturn(editorialVideos, editorialVideos);
        when(videoMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            List<Long> ids = ((java.util.Collection<Long>) invocation.getArgument(0)).stream().toList();
            return editorialVideos.stream().filter(video -> ids.contains(video.getId())).toList();
        });
        when(assembler.toVideoVOList(any(), any())).thenAnswer(invocation ->
                ((List<Video>) invocation.getArgument(0)).stream().map(video -> {
                    VideoVO vo = new VideoVO();
                    vo.setId(video.getId());
                    vo.setTitle(video.getTitle());
                    return vo;
                }).toList()
        );
        when(exposureLogService.logRecommendationExposureFromVideos(any(), anyString(), anyInt(), anyInt(), any(), any(Map.class), anyString()))
                .thenReturn("req-keep-pagination");

        IPage<VideoVO> page = service.listRecommended(2, 2, 1L, Set.of());

        assertThat(page.getRecords()).extracting(VideoVO::getId).containsExactly(13L, 14L);
        verify(exposureLogService).logRecommendationExposureFromVideos(eq(1L), eq("recommended"), eq(2), eq(2), any(), any(Map.class), anyString());
    }

    @Test
    void shouldKeepOldSignatureDelegatingWithoutExcludeIds() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);
        UserProfileSummaryService userProfileSummaryService = mock(UserProfileSummaryService.class);
        VideoViewAssembler assembler = mock(VideoViewAssembler.class);
        RecExposureLogService exposureLogService = mock(RecExposureLogService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        RecommendationServiceImpl service = new RecommendationServiceImpl(
                videoMapper,
                videoTagFeatureMapper,
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                recommendationFeatureService,
                userProfileSummaryService,
                assembler,
                exposureLogService,
                redisTemplate
        );

        List<Video> editorialVideos = List.of(video(11L, true, 100), video(12L, true, 90));
        when(videoMapper.selectList(any())).thenReturn(editorialVideos, editorialVideos);
        when(videoMapper.selectBatchIds(anyCollection())).thenReturn(editorialVideos);
        when(assembler.toVideoVOList(any(), any())).thenAnswer(invocation ->
                ((List<Video>) invocation.getArgument(0)).stream().map(video -> {
                    VideoVO vo = new VideoVO();
                    vo.setId(video.getId());
                    vo.setTitle(video.getTitle());
                    return vo;
                }).toList()
        );
        when(exposureLogService.logRecommendationExposureFromVideos(any(), anyString(), anyInt(), anyInt(), any(), any(Map.class), anyString()))
                .thenReturn("req-2");

        IPage<VideoVO> page = service.listRecommended(1, 2, 1L);

        assertThat(page.getRecords()).extracting(VideoVO::getId).containsExactly(11L, 12L);
        verify(videoMapper, never()).selectBatchIds(Set.of());
    }

    @Test
    void shouldExpandRecommendationWindowWhenSeenIdsExhaustInitialWindow() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);
        UserProfileSummaryService userProfileSummaryService = mock(UserProfileSummaryService.class);
        VideoViewAssembler assembler = mock(VideoViewAssembler.class);
        RecExposureLogService exposureLogService = mock(RecExposureLogService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        RecommendationServiceImpl service = new RecommendationServiceImpl(
                videoMapper,
                videoTagFeatureMapper,
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                recommendationFeatureService,
                userProfileSummaryService,
                assembler,
                exposureLogService,
                redisTemplate
        );

        List<Video> videos = LongStream.rangeClosed(1, 70)
                .mapToObj(id -> video(id, true, 1000 - id))
                .toList();
        Set<Long> seenIds = LongStream.rangeClosed(1, 50)
                .boxed()
                .collect(Collectors.toSet());

        when(videoMapper.selectList(any())).thenReturn(videos, videos);
        when(videoMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            List<Long> ids = ((java.util.Collection<Long>) invocation.getArgument(0)).stream().toList();
            return videos.stream().filter(video -> ids.contains(video.getId())).toList();
        });
        when(assembler.toVideoVOList(any(), any())).thenAnswer(invocation ->
                ((List<Video>) invocation.getArgument(0)).stream().map(video -> {
                    VideoVO vo = new VideoVO();
                    vo.setId(video.getId());
                    vo.setTitle(video.getTitle());
                    return vo;
                }).toList()
        );
        when(exposureLogService.logRecommendationExposureFromVideos(any(), anyString(), anyInt(), anyInt(), any(), any(Map.class), anyString()))
                .thenReturn("req-expanded");

        IPage<VideoVO> page = service.listRecommended(4, 16, 1L, seenIds);

        assertThat(page.getRecords())
                .hasSize(16)
                .extracting(VideoVO::getId)
                .doesNotContainAnyElementsOf(seenIds);
    }

    private Video video(Long id, boolean recommended, long playCount) {
        Video video = new Video();
        video.setId(id);
        video.setTitle("video-" + id);
        video.setAuthorId(id);
        video.setCategoryId(1L);
        video.setIsRecommended(recommended);
        video.setPlayCount(playCount);
        video.setLikeCount(0L);
        video.setSaveCount(0L);
        video.setCreateTime(LocalDateTime.now().minusDays(id));
        return video;
    }
}