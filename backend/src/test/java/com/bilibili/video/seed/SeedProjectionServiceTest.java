package com.bilibili.video.seed;

import com.bilibili.video.common.Constants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.UserInterestTag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.UserInterestTagMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedProjectionServiceTest {

    private WatchHistoryMapper watchHistoryMapper;
    private VideoLikeMapper videoLikeMapper;
    private FavoriteMapper favoriteMapper;
    private FollowMapper followMapper;
    private UserInterestTagMapper userInterestTagMapper;
    private VideoMapper videoMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private ZSetOperations<String, Object> zSetOperations;
    private SeedProjectionService service;

    @BeforeEach
    void setUp() {
        watchHistoryMapper = mock(WatchHistoryMapper.class);
        videoLikeMapper = mock(VideoLikeMapper.class);
        favoriteMapper = mock(FavoriteMapper.class);
        followMapper = mock(FollowMapper.class);
        userInterestTagMapper = mock(UserInterestTagMapper.class);
        videoMapper = mock(VideoMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        service = new SeedProjectionService(
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                userInterestTagMapper,
                videoMapper,
                redisTemplate
        );
    }

    @Test
    void shouldPersistInteractionsAndRefreshHotRankTtl() {
        WatchHistory watch = new WatchHistory();
        watch.setUserId(7L);
        watch.setVideoId(11L);
        watch.setWatchSeconds(120);
        watch.setCreateTime(LocalDateTime.now());

        VideoLike like = new VideoLike();
        like.setUserId(7L);
        like.setVideoId(11L);

        Favorite favorite = new Favorite();
        favorite.setUserId(7L);
        favorite.setVideoId(11L);

        Follow follow = new Follow();
        follow.setFollowerId(7L);
        follow.setFollowingId(8L);

        SeedBehaviorResult result = new SeedBehaviorResult(
                List.of(watch),
                List.of(like),
                List.of(favorite),
                List.of(follow),
                Map.of(11L, 30L),
                Map.of(11L, 6L),
                Map.of(11L, 4L),
                Map.of(21L, Map.of(301L, 4.5D, 302L, 2.5D))
        );

        service.persist(result);

        verify(watchHistoryMapper).insert(watch);
        verify(videoLikeMapper).insert(like);
        verify(favoriteMapper).insert(favorite);
        verify(followMapper).insert(follow);
        verify(userInterestTagMapper, times(2)).insert(any(UserInterestTag.class));
        verify(videoMapper).selectById(11L);
        verify(videoMapper).updateById(any(Video.class));
        verify(zSetOperations).incrementScore(
                Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                "11",
                30D * Constants.HOT_WEIGHT_PLAY + 6D * Constants.HOT_WEIGHT_LIKE + 4D * Constants.HOT_WEIGHT_FAVORITE
        );
        verify(redisTemplate).expire(
                Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                Constants.HOT_WINDOW_HOURS,
                TimeUnit.HOURS
        );
    }

    @Test
    void shouldMergeExistingWatchInterestAndVideoStatsWhenAppendModeHitsUniqueKeys() {
        WatchHistory existingWatch = new WatchHistory();
        existingWatch.setId(1L);
        existingWatch.setUserId(7L);
        existingWatch.setVideoId(11L);
        existingWatch.setWatchSeconds(80);

        UserInterestTag existingInterest = new UserInterestTag();
        existingInterest.setId(2L);
        existingInterest.setUserId(21L);
        existingInterest.setTagId(301L);
        existingInterest.setWeight(1.5D);

        Video existingVideo = new Video();
        existingVideo.setId(11L);
        existingVideo.setPlayCount(100L);
        existingVideo.setLikeCount(10L);
        existingVideo.setSaveCount(5L);

        when(watchHistoryMapper.selectOne(any())).thenReturn(existingWatch);
        when(userInterestTagMapper.selectOne(any())).thenReturn(existingInterest);
        when(videoMapper.selectById(11L)).thenReturn(existingVideo);

        WatchHistory duplicateWatch = new WatchHistory();
        duplicateWatch.setUserId(7L);
        duplicateWatch.setVideoId(11L);
        duplicateWatch.setWatchSeconds(120);
        duplicateWatch.setUpdateTime(LocalDateTime.now());

        SeedBehaviorResult result = new SeedBehaviorResult(
                List.of(duplicateWatch),
                List.of(),
                List.of(),
                List.of(),
                Map.of(11L, 30L),
                Map.of(11L, 6L),
                Map.of(11L, 4L),
                Map.of(21L, Map.of(301L, 4.5D))
        );

        service.persist(result);

        verify(watchHistoryMapper, times(0)).insert(any(WatchHistory.class));
        verify(watchHistoryMapper).updateById(any(WatchHistory.class));

        ArgumentCaptor<UserInterestTag> interestCaptor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(userInterestTagMapper).updateById(interestCaptor.capture());
        assertThat(interestCaptor.getValue().getWeight()).isEqualTo(6.0D);

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoMapper).updateById(videoCaptor.capture());
        assertThat(videoCaptor.getValue().getPlayCount()).isEqualTo(130L);
        assertThat(videoCaptor.getValue().getLikeCount()).isEqualTo(16L);
        assertThat(videoCaptor.getValue().getSaveCount()).isEqualTo(9L);
        verify(zSetOperations).incrementScore(anyString(), any(), eq(30D * Constants.HOT_WEIGHT_PLAY + 6D * Constants.HOT_WEIGHT_LIKE + 4D * Constants.HOT_WEIGHT_FAVORITE));
        verify(redisTemplate).expire(anyString(), anyLong(), eq(TimeUnit.HOURS));
    }
}