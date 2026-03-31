package com.bilibili.video.service.impl;

import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoTag;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagMapper;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 视频写服务：
 * 负责上传、更新、删除、播放统计等会修改状态的操作。
 */
public class VideoCommandService {

    public static final String DEFAULT_COVER_OBJECT = "default/default-video-cover.png";
    private static final String PLAY_COUNT_KEY = RedisConstants.VIDEO_STATS_KEY_PREFIX;
    private static final long PLAY_COUNT_EXPIRE = RedisConstants.VIDEO_STATS_EXPIRE_DAYS;
    private static final TimeUnit PLAY_COUNT_EXPIRE_UNIT = TimeUnit.DAYS;

    private final VideoMapper videoMapper;
    private final VideoTagMapper videoTagMapper;
    private final MinioUtils minioUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoCacheService videoCacheService;
    private final MQService mqService;
    private final VideoViewAssembler videoViewAssembler;
    private final VideoPostProcessFallbackService videoPostProcessFallbackService;

    /**
     * 上传视频并创建视频记录
     */
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

        saveTags(video.getId(), dto.getTagIds());

        mqService.sendVideoProcess(new VideoProcessMessage(video.getId(), authorId));
        mqService.sendSearchSync(new SearchSyncMessage("video", video.getId(), "create"));
        mqService.sendVideoCoverProcess(new VideoProcessMessage(video.getId(), authorId));
        if (useDefaultCover) {
            videoPostProcessFallbackService.triggerCoverProcessFallback(video.getId());
        }
        incrHotScore(video.getId(), Constants.HOT_WEIGHT_PLAY);

        return videoViewAssembler.toVideoVO(video, authorId);
    }

    /**
     * 更新视频信息，并统一失效缓存与同步搜索索引
     */
    public void updateVideo(Video video) {
        videoMapper.updateById(video);
        videoCacheService.invalidateVideo(video.getId());
        mqService.sendSearchSync(new SearchSyncMessage("video", video.getId(), "update"));
    }

    /**
     * 记录视频播放量
     */
    public void recordPlayCount(Long videoId) {
        String key = PLAY_COUNT_KEY + videoId;
        redisTemplate.opsForHash().increment(key, RedisConstants.VIDEO_STAT_PLAY, 1);
        redisTemplate.expire(key, PLAY_COUNT_EXPIRE, PLAY_COUNT_EXPIRE_UNIT);
        incrHotScore(videoId, Constants.HOT_WEIGHT_PLAY);
    }

    /**
     * 设置视频推荐状态
     */
    public void setRecommended(Long videoId, boolean recommended) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            video.setIsRecommended(recommended);
            videoMapper.updateById(video);
            videoCacheService.invalidateVideo(videoId);
        }
    }

    /**
     * 删除视频，并清理缓存、对象存储和搜索索引
     */
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
        videoCacheService.invalidateVideo(videoId);

        try {
            minioUtils.deleteVideoByUrl(video.getVideoUrl());
            minioUtils.deleteCoverByObjectName(video.getCoverUrl());
        } catch (Exception e) {
            log.warn("删除 MinIO 资源失败: videoId={}, videoUrl={}, coverObject={}", videoId, video.getVideoUrl(), video.getCoverUrl(), e);
        }

        mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "delete"));
    }

    /**
     * 保存视频标签关联
     */
    private void saveTags(Long videoId, java.util.List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        Set<Long> uniqueTagIds = tagIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (Long tagId : uniqueTagIds) {
            VideoTag relation = new VideoTag();
            relation.setVideoId(videoId);
            relation.setTagId(tagId);
            videoTagMapper.insert(relation);
        }
    }

    /**
     * 累加热门榜分数
     */
    private void incrHotScore(Long videoId, double delta) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().incrementScore(key, videoId.toString(), delta);
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }

    /**
     * 校验上传视频文件格式
     */
    private void validateVideoFile(MultipartFile videoFile) {
        String name = videoFile.getOriginalFilename();
        String ext = "";
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }

        Set<String> allowed = Set.of("mp4", "mov", "mkv", "webm", "avi", "flv", "m4v");
        if (!allowed.contains(ext)) {
            throw new BizException(400, "暂不支持该视频格式，支持：mp4/mov/mkv/webm/avi/flv/m4v");
        }

        String contentType = videoFile.getContentType();
        if (contentType != null && !contentType.startsWith("video/")) {
            throw new BizException(400, "上传文件不是视频类型");
        }
    }
}
