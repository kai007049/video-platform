package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.entity.UserInterestTag;
import com.kai.videoplatform.entity.VideoTag;
import com.kai.videoplatform.entity.VideoTagFeature;
import com.kai.videoplatform.mapper.UserInterestTagMapper;
import com.kai.videoplatform.mapper.VideoTagFeatureMapper;
import com.kai.videoplatform.mapper.VideoTagMapper;
import com.kai.videoplatform.service.RecommendationFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationFeatureServiceImpl implements RecommendationFeatureService {

    private final VideoTagMapper videoTagMapper;
    private final VideoTagFeatureMapper videoTagFeatureMapper;
    private final UserInterestTagMapper userInterestTagMapper;

    @Override
    @Transactional
    public void syncVideoTagFeatures(Long videoId, List<Long> tagIds, String source, String version) {
        syncVideoTagFeatures(videoId, tagIds, source, version, Collections.emptyMap());
    }

    @Override
    @Transactional
    public void syncVideoTagFeatures(Long videoId,
                                     List<Long> tagIds,
                                     String source,
                                     String version,
                                     Map<Long, Double> confidenceByTagId) {
        if (videoId == null) {
            return;
        }

        videoTagFeatureMapper.delete(new LambdaQueryWrapper<VideoTagFeature>()
                .eq(VideoTagFeature::getVideoId, videoId));

        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        Set<Long> uniqueTagIds = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                uniqueTagIds.add(tagId);
            }
        }

        String normalizedSource = (source == null || source.isBlank()) ? "manual" : source;
        String normalizedVersion = (version == null || version.isBlank()) ? "v1" : version;

        for (Long tagId : uniqueTagIds) {
            VideoTagFeature feature = new VideoTagFeature();
            feature.setVideoId(videoId);
            feature.setTagId(tagId);
            feature.setSource(normalizedSource);
            feature.setVersion(normalizedVersion);
            feature.setConfidence(resolveConfidence(normalizedSource, tagId, confidenceByTagId));
            videoTagFeatureMapper.insert(feature);
        }
    }

    @Override
    @Transactional
    public void increaseUserInterestByVideo(Long userId, Long videoId, double delta) {
        if (userId == null || videoId == null || delta <= 0) {
            return;
        }

        List<VideoTag> relations = videoTagMapper.selectList(new LambdaQueryWrapper<VideoTag>()
                .eq(VideoTag::getVideoId, videoId));
        if (relations == null || relations.isEmpty()) {
            return;
        }

        Set<Long> tagIds = relations.stream()
                .map(VideoTag::getTagId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (tagIds.isEmpty()) {
            return;
        }

        for (Long tagId : tagIds) {
            addUserInterestWeight(userId, tagId, delta);
        }
    }

    @Override
    public List<Long> listTopInterestTagIds(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return java.util.Collections.emptyList();
        }

        List<UserInterestTag> rows = userInterestTagMapper.selectList(new LambdaQueryWrapper<UserInterestTag>()
                .eq(UserInterestTag::getUserId, userId)
                .orderByDesc(UserInterestTag::getWeight)
                .last("limit " + Math.min(limit, 100)));
        if (rows == null || rows.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return rows.stream()
                .map(UserInterestTag::getTagId)
                .filter(Objects::nonNull)
                .toList();
    }

    private double resolveConfidence(String source, Long tagId, Map<Long, Double> confidenceByTagId) {
        Double specified = confidenceByTagId == null ? null : confidenceByTagId.get(tagId);
        if (specified != null) {
            return clamp01(specified);
        }
        // 默认策略：手动标签=1.0，AI标签=0.6，其他来源=0.8
        if ("manual".equalsIgnoreCase(source)) {
            return 1.0D;
        }
        if ("ai".equalsIgnoreCase(source)) {
            return 0.6D;
        }
        return 0.8D;
    }

    private double clamp01(double value) {
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    private void addUserInterestWeight(Long userId, Long tagId, double delta) {
        UserInterestTag row = userInterestTagMapper.selectOne(new LambdaQueryWrapper<UserInterestTag>()
                .eq(UserInterestTag::getUserId, userId)
                .eq(UserInterestTag::getTagId, tagId)
                .last("limit 1"));
        if (row != null) {
            double nextWeight = (row.getWeight() == null ? 0D : row.getWeight()) + delta;
            row.setWeight(Math.min(nextWeight, 10000D));
            userInterestTagMapper.updateById(row);
            return;
        }

        UserInterestTag insertRow = new UserInterestTag();
        insertRow.setUserId(userId);
        insertRow.setTagId(tagId);
        insertRow.setWeight(Math.min(delta, 10000D));
        try {
            userInterestTagMapper.insert(insertRow);
        } catch (DuplicateKeyException ignore) {
            UserInterestTag latest = userInterestTagMapper.selectOne(new LambdaQueryWrapper<UserInterestTag>()
                    .eq(UserInterestTag::getUserId, userId)
                    .eq(UserInterestTag::getTagId, tagId)
                    .last("limit 1"));
            if (latest != null) {
                double nextWeight = (latest.getWeight() == null ? 0D : latest.getWeight()) + delta;
                latest.setWeight(Math.min(nextWeight, 10000D));
                userInterestTagMapper.updateById(latest);
            }
        }
    }
}
