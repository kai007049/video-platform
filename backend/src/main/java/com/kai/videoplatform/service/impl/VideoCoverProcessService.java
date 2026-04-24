package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.service.VideoCacheService;
import com.kai.videoplatform.utils.VideoCoverExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCoverProcessService {

    private final VideoMapper videoMapper;
    private final VideoCoverExtractor videoCoverExtractor;
    private final VideoCacheService videoCacheService;

    public void processByVideoId(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            log.warn("[MQ] video does not exist: {}", videoId);
            return;
        }

        // Only generate a cover when the current cover is still the default placeholder.
        if (video.getCoverUrl() != null
                && !video.getCoverUrl().isBlank()
                && !VideoCommandService.DEFAULT_COVER_OBJECT.equals(video.getCoverUrl())) {
            return;
        }

        String coverUrl = videoCoverExtractor.extractAndUploadCover(video.getVideoUrl());
        if (coverUrl == null || coverUrl.isBlank()) {
            throw new RuntimeException("cover generation returned empty result, videoId=" + videoId);
        }

        videoMapper.update(
                null,
                new LambdaUpdateWrapper<Video>()
                        .set(Video::getCoverUrl, coverUrl)
                        .eq(Video::getId, video.getId())
        );
        videoCacheService.invalidateVideo(video.getId());
        log.info("[MQ] cover generation completed: videoId={}", video.getId());
    }
}