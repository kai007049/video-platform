package com.kai.videoplatform.service;

import com.kai.videoplatform.client.dto.ContentAnalysisResult;

/**
 * 视频自动标注服务
 */
public interface VideoAutoTagService {

    /**
     * 自动分析视频内容并补充标签和分类
     */
    void autoTagVideo(Long videoId);
}