package com.bilibili.video.service.impl;

import com.bilibili.video.entity.RecExposureLog;
import com.bilibili.video.mapper.RecExposureLogMapper;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.RecExposureLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 推荐曝光日志服务实现。
 * 按结果顺序写入曝光记录，为 CTR/A-B/问题回放提供基础数据。
 */
public class RecExposureLogServiceImpl implements RecExposureLogService {

    private final RecExposureLogMapper recExposureLogMapper;

    @Override
    public String logRecommendationExposureBatch(Long userId,
                                                 String scene,
                                                 int page,
                                                 int size,
                                                 List<RecommendationServiceImpl.RecommendationCandidate> candidates,
                                                 String strategyVersion) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String reqId = UUID.randomUUID().toString().replace("-", "");
        int baseRank = Math.max(page - 1, 0) * Math.max(size, 1);
        for (int i = 0; i < candidates.size(); i++) {
            RecommendationServiceImpl.RecommendationCandidate candidate = candidates.get(i);
            if (candidate == null || candidate.getVideo() == null || candidate.getVideo().getId() == null) {
                continue;
            }
            try {
                RecExposureLog row = new RecExposureLog();
                row.setUserId(userId);
                row.setVideoId(candidate.getVideo().getId());
                row.setReqId(reqId);
                row.setScene(scene == null || scene.isBlank() ? "recommended" : scene);
                row.setRank(baseRank + i + 1);
                row.setScore(candidate.getFinalScore());
                row.setChannels(candidate.getRecallChannels().stream().sorted().collect(Collectors.joining(",")));
                row.setStrategyVersion(strategyVersion);
                recExposureLogMapper.insert(row);
            } catch (Exception e) {
                log.debug("recommendation exposure insert failed, reqId={}, videoId={}", reqId, candidate.getVideo().getId(), e);
            }
        }
        return reqId;
    }

    @Override
    public String logRecommendationExposureFromVideos(Long userId,
                                                      String scene,
                                                      int page,
                                                      int size,
                                                      List<VideoVO> videos,
                                                      Map<Long, CachedRecommendationMeta> metaMap,
                                                      String strategyVersion) {
        if (videos == null || videos.isEmpty()) {
            return null;
        }
        String reqId = UUID.randomUUID().toString().replace("-", "");
        int baseRank = Math.max(page - 1, 0) * Math.max(size, 1);
        for (int i = 0; i < videos.size(); i++) {
            VideoVO vo = videos.get(i);
            if (vo == null || vo.getId() == null) {
                continue;
            }
            try {
                CachedRecommendationMeta meta = metaMap == null ? null : metaMap.get(vo.getId());
                RecExposureLog row = new RecExposureLog();
                row.setUserId(userId);
                row.setVideoId(vo.getId());
                row.setReqId(reqId);
                row.setScene(scene == null || scene.isBlank() ? "recommended" : scene);
                row.setRank(baseRank + i + 1);
                row.setScore(meta == null ? null : meta.getScore());
                row.setChannels(meta == null || meta.getChannels() == null ? null : String.join(",", meta.getChannels()));
                row.setStrategyVersion(strategyVersion);
                recExposureLogMapper.insert(row);
            } catch (Exception e) {
                log.debug("cached recommendation exposure insert failed, reqId={}, videoId={}", reqId, vo.getId(), e);
            }
        }
        return reqId;
    }

    @Override
    public String logExposureBatch(Long userId, String scene, int page, int size, List<VideoVO> videos) {
        if (videos == null || videos.isEmpty()) {
            return null;
        }
        String reqId = UUID.randomUUID().toString().replace("-", "");
        int baseRank = Math.max(page - 1, 0) * Math.max(size, 1);
        for (int i = 0; i < videos.size(); i++) {
            VideoVO vo = videos.get(i);
            if (vo == null || vo.getId() == null) {
                continue;
            }
            try {
                RecExposureLog row = new RecExposureLog();
                row.setUserId(userId);
                row.setVideoId(vo.getId());
                row.setReqId(reqId);
                row.setScene(scene == null || scene.isBlank() ? "recommended" : scene);
                row.setRank(baseRank + i + 1);
                row.setStrategyVersion("legacy-v1");
                recExposureLogMapper.insert(row);
            } catch (Exception e) {
                log.debug("generic exposure insert failed, reqId={}, videoId={}", reqId, vo.getId(), e);
            }
        }
        return reqId;
    }
}
