package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Comment;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.User;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.CommentMapper;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.model.vo.VideoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VideoViewAssembler {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public VideoVO toVideoVO(Video video, Long userId) {
        if (video == null) {
            return null;
        }
        List<VideoVO> records = toVideoVOList(Collections.singletonList(video), userId);
        return records.isEmpty() ? null : records.get(0);
    }

    public List<VideoVO> toVideoVOList(List<Video> videos, Long userId) {
        if (videos == null || videos.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> videoIds = videos.stream()
                .map(Video::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> authorIds = videos.stream()
                .map(Video::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, User> authorMap = loadAuthors(authorIds);
        Map<Long, Long> commentCountMap = loadCommentCounts(videoIds);
        Map<Long, Boolean> likedMap = loadLikedState(userId, videoIds);
        Map<Long, Boolean> favoritedMap = loadFavoritedState(userId, videoIds);
        Map<Long, Integer> watchProgressMap = loadWatchProgress(userId, videoIds);
        Map<Long, VideoStats> statsMap = loadStats(videos);

        List<VideoVO> result = new ArrayList<>(videos.size());
        for (Video video : videos) {
            VideoVO vo = new VideoVO();
            BeanUtils.copyProperties(video, vo);
            vo.setPlayUrl("/api/video/" + video.getId() + "/stream");

            User author = authorMap.get(video.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getUsername());
                vo.setAuthorAvatar(author.getAvatar());
            }

            VideoStats stats = statsMap.get(video.getId());
            if (stats != null) {
                vo.setPlayCount(stats.playCount());
                vo.setLikeCount(stats.likeCount());
                vo.setSaveCount(stats.saveCount());
            }

            vo.setCommentCount(commentCountMap.getOrDefault(video.getId(), 0L));
            if (userId != null) {
                vo.setLiked(likedMap.getOrDefault(video.getId(), false));
                vo.setFavorited(favoritedMap.getOrDefault(video.getId(), false));
                if (watchProgressMap.containsKey(video.getId())) {
                    vo.setLastWatchSeconds(watchProgressMap.get(video.getId()));
                }
            }
            result.add(vo);
        }
        return result;
    }

    public VideoVO enrichUserState(VideoVO source, Long userId) {
        if (source == null || userId == null) {
            return source;
        }

        Map<Long, Boolean> likedMap = loadLikedState(userId, Collections.singletonList(source.getId()));
        Map<Long, Boolean> favoritedMap = loadFavoritedState(userId, Collections.singletonList(source.getId()));
        Map<Long, Integer> watchProgressMap = loadWatchProgress(userId, Collections.singletonList(source.getId()));

        VideoVO copy = new VideoVO();
        BeanUtils.copyProperties(source, copy);
        copy.setLiked(likedMap.getOrDefault(source.getId(), false));
        copy.setFavorited(favoritedMap.getOrDefault(source.getId(), false));
        if (watchProgressMap.containsKey(source.getId())) {
            copy.setLastWatchSeconds(watchProgressMap.get(source.getId()));
        }
        return copy;
    }

    private Map<Long, User> loadAuthors(Collection<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, HashMap::new));
    }

    private Map<Long, Long> loadCommentCounts(List<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }

        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("video_id", "COUNT(*) AS cnt")
                .in("video_id", videoIds)
                .groupBy("video_id");

        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : commentMapper.selectMaps(queryWrapper)) {
            Long videoId = toLong(row.get("video_id"));
            Long count = toLong(firstNonNull(row, "cnt", "CNT"));
            if (videoId != null && count != null) {
                result.put(videoId, count);
            }
        }
        return result;
    }

    private Map<Long, Boolean> loadLikedState(Long userId, List<Long> videoIds) {
        if (userId == null || videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return videoLikeMapper.selectList(new LambdaQueryWrapper<VideoLike>()
                        .select(VideoLike::getVideoId)
                        .eq(VideoLike::getUserId, userId)
                        .in(VideoLike::getVideoId, videoIds))
                .stream()
                .map(VideoLike::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(videoId -> videoId, videoId -> true, (left, right) -> left, HashMap::new));
    }

    private Map<Long, Boolean> loadFavoritedState(Long userId, List<Long> videoIds) {
        if (userId == null || videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .select(Favorite::getVideoId)
                        .eq(Favorite::getUserId, userId)
                        .in(Favorite::getVideoId, videoIds))
                .stream()
                .map(Favorite::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(videoId -> videoId, videoId -> true, (left, right) -> left, HashMap::new));
    }

    private Map<Long, Integer> loadWatchProgress(Long userId, List<Long> videoIds) {
        if (userId == null || videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                        .select(WatchHistory::getVideoId, WatchHistory::getWatchSeconds)
                        .eq(WatchHistory::getUserId, userId)
                        .in(WatchHistory::getVideoId, videoIds))
                .stream()
                .filter(history -> history.getVideoId() != null)
                .collect(Collectors.toMap(
                        WatchHistory::getVideoId,
                        WatchHistory::getWatchSeconds,
                        (left, right) -> right,
                        HashMap::new
                ));
    }

    private Map<Long, VideoStats> loadStats(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, VideoStats> result = new LinkedHashMap<>();
        for (Video video : videos) {
            String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + video.getId();
            List<Object> deltas = redisTemplate.opsForHash().multiGet(
                    statsKey,
                    List.of(
                            RedisConstants.VIDEO_STAT_PLAY,
                            RedisConstants.VIDEO_STAT_LIKE,
                            RedisConstants.VIDEO_STAT_SAVE
                    )
            );
            if (deltas == null) {
                deltas = Collections.emptyList();
            }
            long playDelta = deltas.size() > 0 ? toLong(deltas.get(0), 0L) : 0L;
            long likeDelta = deltas.size() > 1 ? toLong(deltas.get(1), 0L) : 0L;
            long saveDelta = deltas.size() > 2 ? toLong(deltas.get(2), 0L) : 0L;

            result.put(video.getId(), new VideoStats(
                    safeLong(video.getPlayCount()) + playDelta,
                    safeLong(video.getLikeCount()) + likeDelta,
                    safeLong(video.getSaveCount()) + saveDelta
            ));
        }
        return result;
    }

    private Object firstNonNull(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        return toLong(value, null);
    }

    private Long toLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private record VideoStats(long playCount, long likeCount, long saveCount) {
    }
}
