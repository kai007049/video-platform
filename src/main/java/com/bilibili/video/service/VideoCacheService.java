package com.bilibili.video.service;

import com.bilibili.video.model.vo.VideoVO;

public interface VideoCacheService {

    VideoVO getVideoFromCache(Long videoId);

    VideoVO getVideoWithLoader(Long videoId, java.util.function.Supplier<VideoVO> loader);

    void setVideoCache(Long videoId, VideoVO videoVO);

    void evictVideoCache(Long videoId);

    void doubleDeleteVideoCache(Long videoId);
}
