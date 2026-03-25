package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.entity.Comment;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.User;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.VideoTag;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.CommentMapper;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.FavoriteService;
import com.bilibili.video.service.LikeService;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.service.VideoService;
import com.bilibili.video.service.WatchHistoryService;
import com.bilibili.video.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * 视频服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoServiceImpl implements VideoService {

    private static final String PLAY_COUNT_KEY = RedisConstants.VIDEO_STATS_KEY_PREFIX;
    private static final String HOT_RANK_KEY = "video:hot:rank";
    private static final long PLAY_COUNT_EXPIRE = RedisConstants.VIDEO_STATS_EXPIRE_DAYS;
    private static final TimeUnit PLAY_COUNT_EXPIRE_UNIT = TimeUnit.DAYS;

    private final VideoMapper videoMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final MinioUtils minioUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final WatchHistoryService watchHistoryService;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoTagMapper videoTagMapper;
    private final VideoCacheService videoCacheService;
    private final MQService mqService;


    /**
     * 上传视频
     * @param videoFile
     * @param coverFile
     * @param dto
     * @param authorId
     * @return
     */
    @Override
    public VideoVO upload(MultipartFile videoFile, MultipartFile coverFile, VideoUploadDTO dto, Long authorId) {
        if (videoFile == null || videoFile.isEmpty()) {
            throw new BizException(400, "视频文件不能为空");
        }
        validateVideoFile(videoFile);
        if (dto.getCategoryId() == null || dto.getCategoryId() <= 0) {
            throw new BizException(400, "请选择视频分类");
        }

        String videoUrl;
        String coverUrl = null;
        try {
            // 上传视频
            videoUrl = minioUtils.uploadVideo(videoFile);
            if (coverFile != null && !coverFile.isEmpty()) {// 有设置封面时
                // 上传封面
                coverUrl = minioUtils.uploadCover(coverFile);
            }
        } catch (Exception e) {
            throw new BizException(500, "文件上传失败: " + e.getMessage());
        }

        Video video = new Video();
        video.setTitle(dto.getTitle());
        video.setDescription(dto.getDescription());
        video.setAuthorId(authorId);
        video.setCoverUrl(coverUrl);
        video.setVideoUrl(videoUrl);
        video.setPlayCount(0L);
        video.setLikeCount(0L);
        video.setSaveCount(0L);
        video.setDurationSeconds(0);
        video.setIsRecommended(false);
        video.setCategoryId(dto.getCategoryId());
        videoMapper.insert(video);

        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            java.util.Set<Long> uniqueTagIds = dto.getTagIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            for (Long tagId : uniqueTagIds) {
                VideoTag relation = new VideoTag();
                relation.setVideoId(video.getId());
                relation.setTagId(tagId);
                videoTagMapper.insert(relation);
            }
        }

        // 发送MQ消息
        mqService.sendVideoProcess(new com.bilibili.video.model.mq.VideoProcessMessage(video.getId(), authorId));
        mqService.sendSearchSync(new com.bilibili.video.model.mq.SearchSyncMessage("video", video.getId(), "create"));
        mqService.sendVideoCoverProcess(new com.bilibili.video.model.mq.VideoProcessMessage(video.getId(), authorId));
        incrHotScore(video.getId(), Constants.HOT_WEIGHT_PLAY);

        return toVideoVO(video, authorId, likeService.isLiked(video.getId(), authorId), null);
    }

    @Override
    public IPage<VideoVO> list(int page, int size, Long userId) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>().orderByDesc(Video::getCreateTime));

        return result.convert(v -> toVideoVO(v, userId, null, null));
    }

    /**
     * 推荐视频
     * @param page
     * @param size
     * @param userId
     * @return
     */
    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        Page<Video> pageParam = new Page<>(page, size);
        QueryWrapper<Video> qw = new QueryWrapper<>();
        qw.orderByDesc("(play_count + like_count * 5)").orderByDesc("create_time");
        Page<Video> result = videoMapper.selectPage(pageParam, qw);
        return result.convert(v -> toVideoVO(v, userId, null, null));
    }

    /**
     * 作者视频
     * @param authorId
     * @param page
     * @param size
     * @param currentUserId
     * @return
     */
    @Override
    public IPage<VideoVO> listByAuthor(Long authorId, int page, int size, Long currentUserId) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, authorId)
                        .orderByDesc(Video::getCreateTime));
        return result.convert(v -> toVideoVO(v, currentUserId, null, null));
    }

    /**
     * 获取视频详情
     * @param videoId
     * @param userId
     * @return
     */
    @Override
    public VideoVO getById(Long videoId, Long userId) {
        VideoVO vo = getVideoById(videoId);
        if (vo != null && userId != null) {
            String likeKey = RedisConstants.VIDEO_LIKE_KEY_PREFIX + videoId + ":" + userId;
            String favKey = RedisConstants.VIDEO_FAVORITE_KEY_PREFIX + videoId + ":" + userId;
            String watchKey = RedisConstants.VIDEO_WATCH_PROGRESS_KEY_PREFIX + userId;

            Object likeCached = redisTemplate.opsForValue().get(likeKey);
            Object favCached = redisTemplate.opsForValue().get(favKey);
            Object watchCached = redisTemplate.opsForHash().get(watchKey, String.valueOf(videoId));

            Boolean liked = likeCached != null ? true : null;
            Boolean favorited = favCached != null ? true : null;
            Integer lastWatch = null;
            if (watchCached != null) {
                if (watchCached instanceof Number) {
                    lastWatch = ((Number) watchCached).intValue();
                } else {
                    try {
                        lastWatch = Integer.parseInt(watchCached.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (liked == null) {
                liked = likeService.isLiked(videoId, userId);
            }
            if (favorited == null) {
                favorited = favoriteService.isFavorited(userId, videoId);
            }
            if (lastWatch == null) {
                lastWatch = watchHistoryService.getLastWatchSeconds(userId, videoId);
            }

            vo.setLiked(liked);
            vo.setFavorited(favorited);
            if (lastWatch != null) vo.setLastWatchSeconds(lastWatch);
        }
        return vo;
    }


    @Override
    public VideoVO getVideoById(Long id) {
        return videoCacheService.getVideoWithLoader(id, () -> {
            Video video = videoMapper.selectById(id);
            if (video == null) {
                throw new BizException(404, "视频不存在");
            }
            return toVideoVO(video, null, null, null);
        });
    }

    /**
     * 更新视频
     * @param video
     */
    @Override
    public void updateVideo(Video video) {
        videoCacheService.evictVideoCache(video.getId());
        videoMapper.updateById(video);
        videoCacheService.doubleDeleteVideoCache(video.getId());
        mqService.sendSearchSync(new com.bilibili.video.model.mq.SearchSyncMessage("video", video.getId(), "update"));
    }

    /**
     * 播放次数
     * @param videoId
     */
    @Override
    public void recordPlayCount(Long videoId) {
        String key = PLAY_COUNT_KEY + videoId;
        redisTemplate.opsForHash().increment(key, RedisConstants.VIDEO_STAT_PLAY, 1);
        redisTemplate.expire(key, PLAY_COUNT_EXPIRE, PLAY_COUNT_EXPIRE_UNIT);
        incrHotScore(videoId, Constants.HOT_WEIGHT_PLAY);
    }

    /**
     * 设置视频推荐
     * @param videoId
     * @param recommended
     */
    @Override
    public void setRecommended(Long videoId, boolean recommended) {
        Video v = videoMapper.selectById(videoId);
        if (v != null) {
            v.setIsRecommended(recommended);
            videoMapper.updateById(v);
        }
    }

    /**
     * 热门视频
     * @param page
     * @param size
     * @param userId
     * @return
     */
    @Override
    public IPage<VideoVO> listHot(int page, int size, Long userId) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        int start = (page - 1) * size;
        int end = start + size - 1;
        java.util.Set<Object> idSet = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (idSet == null || idSet.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        java.util.List<Long> ids = idSet.stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .collect(java.util.stream.Collectors.toList());
        java.util.List<Video> videos = videoMapper.selectBatchIds(ids);
        java.util.Map<Long, Video> map = videos.stream()
                .collect(java.util.stream.Collectors.toMap(Video::getId, v -> v));
        java.util.List<VideoVO> records = ids.stream()
                .map(map::get)
                .filter(java.util.Objects::nonNull)
                .map(v -> toVideoVO(v, userId, null, null))
                .collect(java.util.stream.Collectors.toList());
        Long total = redisTemplate.opsForZSet().zCard(key);
        Page<VideoVO> result = new Page<>(page, size, total == null ? 0 : total);
        result.setRecords(records);
        return result;
    }

    /**
     * 获取作者视频
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public IPage<VideoVO> listCreatorVideos(Long userId, int page, int size) {
        Page<Video> pageParam = new Page<>(page, size);
        Page<Video> result = videoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, userId)
                        .orderByDesc(Video::getCreateTime));
        return result.convert(v -> toVideoVO(v, userId, null, null));
    }

    /**
     * 查看点赞列表
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public IPage<VideoVO> listLikedVideos(Long userId, int page, int size) {
        Page<VideoLike> pageParam = new Page<>(page, size);
        Page<VideoLike> likes = videoLikeMapper.selectPage(pageParam,
                new LambdaQueryWrapper<VideoLike>()
                        .eq(VideoLike::getUserId, userId)
                        .orderByDesc(VideoLike::getCreateTime));
        return convertToVideoPage(likes, userId);
    }

    /**
     * 查看收藏列表
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public IPage<VideoVO> listFavoriteVideos(Long userId, int page, int size) {
        Page<Favorite> pageParam = new Page<>(page, size);
        Page<Favorite> favorites = favoriteMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));
        return convertToVideoPage(favorites, userId);
    }

    /**
     * 列出观看历史
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public IPage<VideoVO> listHistoryVideos(Long userId, int page, int size) {
        Page<WatchHistory> pageParam = new Page<>(page, size);
        Page<WatchHistory> history = watchHistoryMapper.selectPage(pageParam,
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .orderByDesc(WatchHistory::getUpdateTime));
        return convertToVideoPage(history, userId);
    }

    /**
     * 删除视频
     * @param videoId
     * @param userId
     */
    @Override
    @Transactional
    public void deleteVideo(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        if (!video.getAuthorId().equals(userId)) {
            throw new BizException(403, "无权删除该视频");
        }
        videoMapper.deleteById(videoId);
        // 清除缓存
        videoCacheService.evictVideoCache(videoId);
        try {
            minioUtils.deleteVideoByUrl(video.getVideoUrl());
            minioUtils.deleteCoverByObjectName(video.getCoverUrl());
        } catch (Exception e) {
            log.warn("删除 MinIO 资源失败: videoId={}, videoUrl={}, coverObject={}", videoId, video.getVideoUrl(), video.getCoverUrl(), e);
        }
        mqService.sendSearchSync(new com.bilibili.video.model.mq.SearchSyncMessage("video", videoId, "delete"));
    }


    /**
     * 转换为 VideoVO
     * @param video  数据
     * @param userId 用户ID
     * @param liked 是否点赞
     * @param lastWatchSeconds 观看进度
     * @return
     */
    private VideoVO toVideoVO(Video video, Long userId, Boolean liked, Integer lastWatchSeconds) {
        if (liked == null && userId != null) {
            liked = likeService.isLiked(video.getId(), userId);
        }
        Boolean favorited = userId != null ? favoriteService.isFavorited(userId, video.getId()) : false;
        Integer lastWatch = lastWatchSeconds != null ? lastWatchSeconds : (userId != null ? watchHistoryService.getLastWatchSeconds(userId, video.getId()) : null);
        return toVideoVO(video, null, liked, favorited, lastWatch);
    }

    /**
     * 转换为 VideoVO
     * @param video  数据
     * @param authorId 作者ID
     * @param liked 是否点赞
     * @param favorited 是否收藏
     * @param lastWatchSeconds 观看进度
     * @return
     */
    private VideoVO toVideoVO(Video video, Long authorId, Boolean liked, Boolean favorited, Integer lastWatchSeconds) {
        VideoVO vo = new VideoVO();
        BeanUtils.copyProperties(video, vo);
        vo.setPlayUrl("/api/video/" + video.getId() + "/stream");
        if (liked != null) vo.setLiked(liked);
        if (favorited != null) vo.setFavorited(favorited);
        if (lastWatchSeconds != null) vo.setLastWatchSeconds(lastWatchSeconds);
        if (video.getDurationSeconds() != null) vo.setDurationSeconds(video.getDurationSeconds());
        if (video.getPreviewUrl() != null) vo.setPreviewUrl(video.getPreviewUrl());
        if (video.getIsRecommended() != null) vo.setIsRecommended(video.getIsRecommended());
        if (video.getSaveCount() != null) vo.setSaveCount(video.getSaveCount());
        vo.setCommentCount(commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getVideoId, video.getId())));

        User author = userMapper.selectById(video.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        String statsKey = PLAY_COUNT_KEY + video.getId();
        Long playCount = resolveStat(video.getPlayCount(), statsKey, RedisConstants.VIDEO_STAT_PLAY);
        Long likeCount = resolveStat(video.getLikeCount(), statsKey, RedisConstants.VIDEO_STAT_LIKE);
        Long saveCount = resolveStat(video.getSaveCount(), statsKey, RedisConstants.VIDEO_STAT_SAVE);

        vo.setPlayCount(playCount);
        vo.setLikeCount(likeCount);
        vo.setSaveCount(saveCount);

        return vo;
    }

    private <T> IPage<VideoVO> convertToVideoPage(IPage<T> pageData, Long userId) {
        Page<VideoVO> result = new Page<>(pageData.getCurrent(), pageData.getSize(), pageData.getTotal());
        result.setRecords(pageData.getRecords().stream().map(item -> {
            Long videoId = null;
            if (item instanceof VideoLike like) {
                videoId = like.getVideoId();
            } else if (item instanceof Favorite favorite) {
                videoId = favorite.getVideoId();
            } else if (item instanceof WatchHistory history) {
                videoId = history.getVideoId();
            }
            if (videoId == null) return null;
            Video video = videoMapper.selectById(videoId);
            if (video == null) return null;
            return toVideoVO(video, userId, null, null);
        }).filter(v -> v != null).toList());
        return result;
    }

    /**
     * 更新视频热度
     * @param videoId
     * @param delta
     */
    private void incrHotScore(Long videoId, double delta) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().incrementScore(key, videoId.toString(), delta);
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }

    /**
     * 解析统计数据
     * @param base
     * @param statsKey
     * @param field
     * @return
     */
    private Long resolveStat(Long base, String statsKey, String field) {
        Long value = base == null ? 0L : base;
        Object deltaObj = redisTemplate.opsForHash().get(statsKey, field);
        if (deltaObj != null) {
            value = value + ((Number) deltaObj).longValue();
        }
        return value;
    }

    /**
     * 上传视频文件校验：支持主流容器格式。
     */
    private void validateVideoFile(MultipartFile videoFile) {
        String name = videoFile.getOriginalFilename();
        String ext = "";
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }

        java.util.Set<String> allowed = java.util.Set.of("mp4", "mov", "mkv", "webm", "avi", "flv", "m4v");
        if (!allowed.contains(ext)) {
            throw new BizException(400, "暂不支持该视频格式，支持：mp4/mov/mkv/webm/avi/flv/m4v");
        }

        String contentType = videoFile.getContentType();
        if (contentType != null && !contentType.startsWith("video/")) {
            throw new BizException(400, "上传文件不是视频类型");
        }
    }
}
