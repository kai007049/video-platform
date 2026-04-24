package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.common.Constants;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.Tag;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.entity.VideoTag;
import com.kai.videoplatform.exception.BizException;
import com.kai.videoplatform.mapper.CategoryMapper;
import com.kai.videoplatform.mapper.CommentMapper;
import com.kai.videoplatform.mapper.DanmuMapper;
import com.kai.videoplatform.mapper.FavoriteMapper;
import com.kai.videoplatform.mapper.TagMapper;
import com.kai.videoplatform.mapper.VideoLikeMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.mapper.VideoTagFeatureMapper;
import com.kai.videoplatform.mapper.VideoTagMapper;
import com.kai.videoplatform.mapper.WatchHistoryMapper;
import com.kai.videoplatform.model.dto.VideoUploadDTO;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.kai.videoplatform.model.mq.VideoDeleteMessage;
import com.kai.videoplatform.model.mq.VideoProcessMessage;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.service.MQService;
import com.kai.videoplatform.service.RecommendationFeatureService;
import com.kai.videoplatform.service.VideoCacheService;
import com.kai.videoplatform.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCommandService {

    public static final String DEFAULT_COVER_OBJECT = "default/default-video-cover.png";
    private static final String PLAY_COUNT_KEY = RedisConstants.VIDEO_STATS_KEY_PREFIX;
    private static final long PLAY_COUNT_EXPIRE = RedisConstants.VIDEO_STATS_EXPIRE_DAYS;
    private static final TimeUnit PLAY_COUNT_EXPIRE_UNIT = TimeUnit.DAYS;

    private final VideoMapper videoMapper;
    private final VideoTagMapper videoTagMapper;
    private final VideoTagFeatureMapper videoTagFeatureMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final DanmuMapper danmuMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final MinioUtils minioUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoCacheService videoCacheService;
    private final MQService mqService;
    private final VideoViewAssembler videoViewAssembler;
    private final VideoPostProcessFallbackService videoPostProcessFallbackService;
    private final RecommendationFeatureService recommendationFeatureService;

    /**
     * 上传视频并创建记录。
     * 标题/简介/分类/标签必填，封面可选。
     */
    public VideoVO upload(VideoUploadDTO dto, Long authorId) {
        MultipartFile videoFile = dto.getVideo();
        MultipartFile coverFile = dto.getCover();
        if (videoFile == null || videoFile.isEmpty()) {
            throw new BizException(400, "视频文件不能为空");
        }
        validateVideoFile(videoFile);
        validateManualMetadata(dto);

        String videoUrl;
        String coverUrl;
        boolean useDefaultCover = false;
        try {
            videoUrl = minioUtils.uploadVideo(videoFile);
            if (coverFile != null && !coverFile.isEmpty()) {
                coverUrl = minioUtils.uploadCover(coverFile);
            } else {
                coverUrl = DEFAULT_COVER_OBJECT;
                useDefaultCover = true;
            }
        } catch (Exception e) {
            throw new BizException(500, "文件上传失败: " + e.getMessage());
        }

        String title = dto.getTitle().trim();
        String description = dto.getDescription().trim();
        List<Long> tagIds = dto.getTagIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
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

        saveTags(video.getId(), tagIds);
        recommendationFeatureService.syncVideoTagFeatures(
                video.getId(),
                tagIds,
                "manual",
                "v1",
                buildManualConfidenceMap(tagIds)
        );

        VideoProcessMessage processMessage = new VideoProcessMessage(video.getId(), authorId);
        processMessage.setBizKey("video:" + video.getId() + ":process");
        mqService.sendVideoProcess(processMessage);

        SearchSyncMessage createSyncMessage = new SearchSyncMessage("video", video.getId(), "create");
        createSyncMessage.setBizKey("search:video:" + video.getId() + ":create");
        mqService.sendSearchSync(createSyncMessage);

        VideoProcessMessage coverProcessMessage = new VideoProcessMessage(video.getId(), authorId);
        coverProcessMessage.setBizKey("video:" + video.getId() + ":cover");
        mqService.sendVideoCoverProcess(coverProcessMessage);
        if (useDefaultCover) {
            videoPostProcessFallbackService.triggerCoverProcessFallback(video.getId());
        }
        incrHotScore(video.getId(), Constants.HOT_WEIGHT_PLAY);

        return videoViewAssembler.toVideoVO(video, authorId);
    }

    public void updateVideo(Video video) {
        videoMapper.updateById(video);
        videoCacheService.invalidateVideo(video.getId());
        SearchSyncMessage updateSyncMessage = new SearchSyncMessage("video", video.getId(), "update");
        updateSyncMessage.setBizKey("search:video:" + video.getId() + ":update");
        mqService.sendSearchSync(updateSyncMessage);
    }

    public void recordPlayCount(Long videoId) {
        String key = PLAY_COUNT_KEY + videoId;
        redisTemplate.opsForHash().increment(key, RedisConstants.VIDEO_STAT_PLAY, 1);
        redisTemplate.expire(key, PLAY_COUNT_EXPIRE, PLAY_COUNT_EXPIRE_UNIT);
        incrHotScore(videoId, Constants.HOT_WEIGHT_PLAY);
        // 播放数变化会影响详情页聚合展示，这里主动失效详情缓存，确保短时间内能看到最新值。
        videoCacheService.invalidateVideo(videoId);
    }

    public void setRecommended(Long videoId, boolean recommended) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            video.setIsRecommended(recommended);
            videoMapper.updateById(video);
            videoCacheService.invalidateVideo(videoId);
        }
    }

    @Transactional
    public void deleteVideo(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        if (!video.getAuthorId().equals(userId)) {
            throw new BizException(403, "无权删除该视频");
        }

        // 先同步删除强关联业务数据，保证事务提交后数据库里不再保留 video_id 残留关系。
        deleteVideoRelatedRows(videoId);
        videoMapper.deleteById(videoId);

        // 立即清理与该视频强绑定的缓存和 Redis 状态，避免删除后仍被展示或统计任务继续消费。
        cleanupVideoRuntimeState(videoId);

        // 外部资源和索引属于最终一致性副作用，走 MQ 更适合做重试和容错。
        VideoDeleteMessage deleteMessage = new VideoDeleteMessage(videoId, video.getVideoUrl(), video.getCoverUrl());
        deleteMessage.setBizKey("video:" + videoId + ":delete");
        mqService.sendVideoDelete(deleteMessage);

        SearchSyncMessage deleteSyncMessage = new SearchSyncMessage("video", videoId, "delete");
        deleteSyncMessage.setBizKey("search:video:" + videoId + ":delete");
        mqService.sendSearchSync(deleteSyncMessage);
    }

    /**
     * 删除视频后同步清理所有强关联表，避免孤儿数据残留。
     */
    private void deleteVideoRelatedRows(Long videoId) {
        videoTagMapper.delete(new LambdaQueryWrapper<VideoTag>()
                .eq(com.kai.videoplatform.entity.VideoTag::getVideoId, videoId));
        videoTagFeatureMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.VideoTagFeature>()
                .eq(com.kai.videoplatform.entity.VideoTagFeature::getVideoId, videoId));
        videoLikeMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.VideoLike>()
                .eq(com.kai.videoplatform.entity.VideoLike::getVideoId, videoId));
        favoriteMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.Favorite>()
                .eq(com.kai.videoplatform.entity.Favorite::getVideoId, videoId));
        commentMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.Comment>()
                .eq(com.kai.videoplatform.entity.Comment::getVideoId, videoId));
        danmuMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.Danmu>()
                .eq(com.kai.videoplatform.entity.Danmu::getVideoId, videoId));
        watchHistoryMapper.delete(new LambdaQueryWrapper<com.kai.videoplatform.entity.WatchHistory>()
                .eq(com.kai.videoplatform.entity.WatchHistory::getVideoId, videoId));
    }

    /**
     * 清理视频详情缓存、评论缓存、统计缓存和热榜成员，避免删除后仍然被读取。
     */
    private void cleanupVideoRuntimeState(Long videoId) {
        videoCacheService.invalidateVideo(videoId);
        redisTemplate.delete(RedisConstants.COMMENT_LIST_KEY_PREFIX + videoId);
        redisTemplate.delete(RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId);
        String hotKey = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().remove(hotKey, String.valueOf(videoId));
    }

    private void validateManualMetadata(VideoUploadDTO dto) {
        List<Long> manualTagIds = dto.getTagIds() == null ? Collections.emptyList() : dto.getTagIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        validateManualMetadata(dto.getCategoryId(), manualTagIds);
    }

    private void validateManualMetadata(Long categoryId, List<Long> tagIds) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(400, "分类不存在");
        }
        List<Long> uniqueTagIds = tagIds == null ? Collections.emptyList() : tagIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueTagIds.isEmpty()) {
            return;
        }
        Set<Long> existingTagIds = tagMapper.selectBatchIds(uniqueTagIds).stream()
                .map(Tag::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> missingTagIds = uniqueTagIds.stream()
                .filter(tagId -> !existingTagIds.contains(tagId))
                .toList();
        if (!missingTagIds.isEmpty()) {
            throw new BizException(400, "标签不存在: " + missingTagIds);
        }
    }

    private Map<Long, Double> buildManualConfidenceMap(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagIds.stream()
                .collect(Collectors.toMap(id -> id, id -> 1.0D, (a, b) -> a, LinkedHashMap::new));
    }

    private void saveTags(Long videoId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueTagIds = tagIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long tagId : uniqueTagIds) {
            VideoTag relation = new VideoTag();
            relation.setVideoId(videoId);
            relation.setTagId(tagId);
            videoTagMapper.insert(relation);
        }
    }

    private void incrHotScore(Long videoId, double delta) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().incrementScore(key, videoId.toString(), delta);
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }

    private void validateVideoFile(MultipartFile videoFile) {
        String name = videoFile.getOriginalFilename();
        String ext = "";
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        Set<String> allowed = Set.of("mp4", "mov", "mkv", "webm", "avi", "flv", "m4v");
        if (!allowed.contains(ext)) {
            throw new BizException(400, "暂不支持该视频格式，支持: mp4/mov/mkv/webm/avi/flv/m4v");
        }
        String contentType = videoFile.getContentType();
        if (contentType != null && !contentType.startsWith("video/")) {
            throw new BizException(400, "上传文件不是视频类型");
        }
    }
}