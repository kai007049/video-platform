package com.bilibili.video.service;

import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.impl.RecommendationServiceImpl;

import java.util.List;

public interface RecExposureLogService {

    /**
     * 批量写推荐曝光日志。
     *
     * @return 统一请求ID（reqId），可用于问题排查和链路追踪
     */
    String logRecommendationExposureBatch(Long userId,
                                         String scene,
                                         int page,
                                         int size,
                                         List<RecommendationServiceImpl.RecommendationCandidate> candidates,
                                         String strategyVersion);

    /**
     * 通用曝光日志写入，兼容热门榜等暂未引入候选对象的场景。
     */
    String logExposureBatch(Long userId, String scene, int page, int size, List<VideoVO> videos);
}
