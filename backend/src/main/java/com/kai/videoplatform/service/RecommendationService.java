package com.kai.videoplatform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.model.vo.VideoVO;

import java.util.Set;

public interface RecommendationService {

    /**
     * 推荐主入口：返回给前端的推荐分页结果。
     * 内部可组合多路召回、融合排序和曝光日志。
     */
    IPage<VideoVO> listRecommended(int page, int size, Long userId);

    IPage<VideoVO> listRecommended(int page, int size, Long userId, Set<Long> excludeVideoIds);
}