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
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.CommentMapper;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.FavoriteService;
import com.bilibili.video.service.LikeService;
import com.bilibili.video.common.Constants;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.service.VideoService;
import com.bilibili.video.service.WatchHistoryService;
import com.bilibili.video.utils.MinioUtils;
import com.bilibili.video.utils.VideoCoverExtractor;
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

    private static final String PLAY_COUNT_KEY = "video:play_count:";
    private static final String HOT_RANK_KEY = "video:hot:rank";
    private static final long PLAY_COUNT_EXPIRE = 7;
    private static final TimeUnit PLAY_COUNT_EXPIRE_UNIT = TimeUnit.DAYS;

    private final VideoMapper videoMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final MinioUtils minioUtils;
    private final VideoCoverExtractor videoCoverExtractor;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final WatchHistoryService watchHistoryService;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
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

        String videoUrl;
        String coverUrl = null;
        try {
            videoUrl = minioUtils.uploadVideo(videoFile);
            if (coverFile != null && !coverFile.isEmpty()) {
                coverUrl = minioUtils.uploadCover(coverFile);
            } else {
                coverUrl = videoCoverExtractor.extractAndUploadCover(videoUrl);
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
        videoMapper.insert(video);

        video.setDurationSeconds(0);
        video.setIsRecommended(false);

        mqService.sendVideoProcess(new com.bilibili.video.model.mq.VideoProcessMessage(video.getId(), authorId));
        mqService.sendSearchSync(new com.bilibili.video.model.mq.SearchSyncMessage("video", video.getId(), "create"));
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

    @Override
    public VideoVO getById(Long videoId, Long userId) {
        return getVideoById(videoId);
    }

    @Override
    public VideoVO getVideoById(Long id) {
        VideoVO vo = videoCacheService.getVideoWithLoader(id, () -> {
            Video video = videoMapper.selectById(id);
            if (video == null) {
                return null;
            }
            return toVideoVO(video, null, null, null);
        });
        if (vo == null) {
            throw new BizException(404, "视频不存在");
        }
        return vo;
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
    }

    /**
     * 播放次数
     * @param videoId
     */
    @Override
    public void recordPlayCount(Long videoId) {
        String key = PLAY_COUNT_KEY + videoId;
        redisTemplate.opsForHash().increment(key, "count", 1);
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
        videoCacheService.evictVideoCache(videoId);
        try {
            minioUtils.deleteVideoByUrl(video.getVideoUrl());
            minioUtils.deleteCoverByObjectName(video.getCoverUrl());
        } catch (Exception e) {
            log.warn("删除 MinIO 资源失败: videoId={}, videoUrl={}, coverObject={}", videoId, video.getVideoUrl(), video.getCoverUrl(), e);
        }
        mqService.sendSearchSync(new com.bilibili.video.model.mq.SearchSyncMessage("video", videoId, "delete"));
    }


    private VideoVO toVideoVO(Video video, Long userId, Boolean liked, Integer lastWatchSeconds) {
        if (liked == null && userId != null) {
            liked = likeService.isLiked(video.getId(), userId);
        }
        Boolean favorited = userId != null ? favoriteService.isFavorited(userId, video.getId()) : false;
        Integer lastWatch = lastWatchSeconds != null ? lastWatchSeconds : (userId != null ? watchHistoryService.getLastWatchSeconds(userId, video.getId()) : null);
        return toVideoVO(video, null, liked, favorited, lastWatch);
    }

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

        Long playCount = video.getPlayCount();
        String key = PLAY_COUNT_KEY + video.getId();
        Object redisCount = redisTemplate.opsForHash().get(key, "count");
        if (redisCount != null) {
            playCount = playCount + ((Number) redisCount).longValue();
        }
        vo.setPlayCount(playCount);

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

    private void incrHotScore(Long videoId, double delta) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().incrementScore(key, videoId.toString(), delta);
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }
}
