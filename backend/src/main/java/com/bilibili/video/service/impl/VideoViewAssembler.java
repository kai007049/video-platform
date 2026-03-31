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
import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
/**
 * 视频视图组装器：
 * 负责将 Video 实体批量转换为 VideoVO，并集中处理作者、评论数、用户状态、统计值聚合。
 */
public class VideoViewAssembler {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 组装单个视频展示对象
     */
    public VideoVO toVideoVO(Video video, Long userId) {
        if (video == null) {
            return null;
        }
        List<VideoVO> records = toVideoVOList(Collections.singletonList(video), userId);
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 批量组装视频展示对象。
     * 这里统一批量查询作者、评论数、点赞/收藏状态和观看进度，避免列表场景的 N+1 查询。
     */
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

        Map<Long, User> authorMap = loadAuthors(authorIds);//批量加载作者信息
        Map<Long, Long> commentCountMap = loadCommentCounts(videoIds);//批量加载评论数
        Map<Long, Boolean> likedMap = loadLikedState(userId, videoIds);//批量加载点赞状态
        Map<Long, Boolean> favoritedMap = loadFavoritedState(userId, videoIds);//批量加载收藏状态
        Map<Long, Integer> watchProgressMap = loadWatchProgress(userId, videoIds);//批量加载观看进度
        Map<Long, VideoStats> statsMap = loadStats(videos);//批量加载统计值

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

    /**
     * 在已有 VideoVO 基础上补充当前用户相关状态。
     */
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

    /**
     * 批量加载作者信息
     */
    private Map<Long, User> loadAuthors(Collection<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, HashMap::new));
    }

    /**
     * 批量统计评论数
     */
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

    /**
     * 批量加载点赞状态
     */
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

    /**
     * 批量加载收藏状态
     */
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

    /**
     * 批量加载观看进度
     */
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

    /**
     * 读取视频统计值。
     * 当前仍按视频逐条读取 Redis Hash，但读取逻辑已集中，后续如果要改成 pipeline 或独立统计服务，
     * 只需要调整这里。
     */
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
