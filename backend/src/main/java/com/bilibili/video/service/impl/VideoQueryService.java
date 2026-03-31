package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.common.Constants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.VideoCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 视频查询服务：
 * 负责列表、详情以及与展示相关的聚合查询逻辑。
 */
public class VideoQueryService {

    private final VideoMapper videoMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoCacheService videoCacheService;
    private final VideoViewAssembler videoViewAssembler;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询最新视频列表
     */
    public IPage<VideoVO> list(int page, int size, Long userId) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>().orderByDesc(Video::getCreateTime));
        return convertVideoPage(result, result.getRecords(), userId);
    }

    /**
     * 查询推荐视频列表
     */
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        Page<Video> pageParam = new Page<>(page, size);
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("(play_count + like_count * 5)").orderByDesc("create_time");
        Page<Video> result = videoMapper.selectPage(pageParam, queryWrapper);
        return convertVideoPage(result, result.getRecords(), userId);
    }

    /**
     * 查询热门视频列表
     */
    public IPage<VideoVO> listHot(int page, int size, Long userId) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        int start = (page - 1) * size;
        int end = start + size - 1;
        java.util.Set<Object> rawIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
        List<Long> ids = rawIds == null ? Collections.emptyList() : rawIds.stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .toList();
        if (ids.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        List<Video> videos = loadVideosByIds(ids);
        Long total = redisTemplate.opsForZSet().zCard(key);
        Page<VideoVO> result = new Page<>(page, size, total == null ? 0 : total);
        result.setRecords(videoViewAssembler.toVideoVOList(videos, userId));
        return result;
    }

    /**
     * 按作者查询视频列表
     */
    public IPage<VideoVO> listByAuthor(Long authorId, int page, int size, Long currentUserId) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, authorId)
                        .orderByDesc(Video::getCreateTime));
        return convertVideoPage(result, result.getRecords(), currentUserId);
    }

    /**
     * 查询创作者自己的作品
     */
    public IPage<VideoVO> listCreatorVideos(Long userId, int page, int size) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, userId)
                        .orderByDesc(Video::getCreateTime));
        return convertVideoPage(result, result.getRecords(), userId);
    }

    /**
     * 查询点赞过的视频
     */
    public IPage<VideoVO> listLikedVideos(Long userId, int page, int size) {
        Page<VideoLike> pageParam = new Page<>(page, size);
        Page<VideoLike> likes = videoLikeMapper.selectPage(pageParam,
                new LambdaQueryWrapper<VideoLike>()
                        .eq(VideoLike::getUserId, userId)
                        .orderByDesc(VideoLike::getCreateTime));
        List<Long> videoIds = likes.getRecords().stream().map(VideoLike::getVideoId).toList();
        return convertVideoPage(likes, loadVideosByIds(videoIds), userId);
    }

    /**
     * 查询收藏过的视频
     */
    public IPage<VideoVO> listFavoriteVideos(Long userId, int page, int size) {
        Page<Favorite> pageParam = new Page<>(page, size);
        Page<Favorite> favorites = favoriteMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));
        List<Long> videoIds = favorites.getRecords().stream().map(Favorite::getVideoId).toList();
        return convertVideoPage(favorites, loadVideosByIds(videoIds), userId);
    }

    /**
     * 查询观看历史视频
     */
    public IPage<VideoVO> listHistoryVideos(Long userId, int page, int size) {
        Page<WatchHistory> pageParam = new Page<>(page, size);
        Page<WatchHistory> history = watchHistoryMapper.selectPage(pageParam,
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .orderByDesc(WatchHistory::getUpdateTime));
        List<Long> videoIds = history.getRecords().stream().map(WatchHistory::getVideoId).toList();
        return convertVideoPage(history, loadVideosByIds(videoIds), userId);
    }

    /**
     * 查询视频详情，并补充当前用户状态
     */
    public VideoVO getById(Long videoId, Long userId) {
        VideoVO video = getVideoById(videoId);
        if (video == null || userId == null) {
            return video;
        }
        return videoViewAssembler.enrichUserState(video, userId);
    }

    /**
     * 查询视频基础详情，优先走缓存
     */
    public VideoVO getVideoById(Long videoId) {
        return videoCacheService.getOrLoadVideo(videoId, () -> {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                throw new BizException(404, "视频不存在");
            }
            return videoViewAssembler.toVideoVO(video, null);
        });
    }

    /**
     * 按给定 ID 顺序批量加载视频，避免 selectBatchIds 打乱顺序后影响前端展示。
     */
    private List<Video> loadVideosByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Video> videoMap = videoMapper.selectBatchIds(ids).stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(Video::getId, video -> video, (left, right) -> left, LinkedHashMap::new));
        return ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将视频实体分页结果转换为 VideoVO 分页结果。
     */
    private IPage<VideoVO> convertVideoPage(IPage<?> sourcePage, List<Video> videos, Long userId) {
        Page<VideoVO> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        result.setRecords(videoViewAssembler.toVideoVOList(videos, userId));
        return result;
    }
}
