package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.service.impl.CachedRecommendationMeta;
import com.kai.videoplatform.service.impl.RecommendationServiceImpl;

import java.util.List;
import java.util.Map;

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
     * 基于最终返回的推荐结果和缓存元信息记录曝光日志，确保日志只覆盖用户真正看到的内容。
     */
    String logRecommendationExposureFromVideos(Long userId,
                                               String scene,
                                               int page,
                                               int size,
                                               List<VideoVO> videos,
                                               Map<Long, CachedRecommendationMeta> metaMap,
                                               String strategyVersion);

    /**
     * 通用曝光日志写入，兼容热门榜等暂未引入候选对象的场景。
     */
    String logExposureBatch(Long userId, String scene, int page, int size, List<VideoVO> videos);
}