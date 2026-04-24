package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.VideoVO;

/**
 * 视频缓存服务
 */
public interface VideoCacheService {

    VideoVO getVideoFromCache(Long videoId);

    VideoVO getOrLoadVideo(Long videoId, java.util.function.Supplier<VideoVO> loader);

    void setVideoCache(Long videoId, VideoVO videoVO);

    void invalidateVideo(Long videoId);

    default VideoVO getVideoWithLoader(Long videoId, java.util.function.Supplier<VideoVO> loader) {
        return getOrLoadVideo(videoId, loader);
    }

    default void evictVideoCache(Long videoId) {
        invalidateVideo(videoId);
    }

    default void doubleDeleteVideoCache(Long videoId) {
        invalidateVideo(videoId);
    }
}