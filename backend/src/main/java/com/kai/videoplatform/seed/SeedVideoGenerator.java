package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.entity.VideoTag;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.mapper.VideoTagMapper;
import com.kai.videoplatform.service.RecommendationFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeedVideoGenerator {

    private static final String FEATURE_SOURCE = "seed";
    private static final String FEATURE_VERSION = "v1";
    private static final double HEAD_BASE_SCORE = 0.82D;
    private static final double MID_BASE_SCORE = 0.60D;
    private static final double TAIL_BASE_SCORE = 0.38D;
    private static final double EDITORIAL_PROBABILITY = 0.08D;
    private static final double TAG_CONFIDENCE = 0.90D;
    private static final double HEAD_RECOMMENDATION_THRESHOLD = 0.84D;
    private static final double MID_RECOMMENDATION_THRESHOLD = 0.64D;
    private static final double TAIL_RECOMMENDATION_THRESHOLD = 0.44D;

    private final VideoMapper videoMapper;
    private final VideoTagMapper videoTagMapper;
    private final RecommendationFeatureService recommendationFeatureService;

    public List<SeedVideoProfile> generate(
            SeedProfile profile,
            SeedDomainSnapshot snapshot,
            SeedPopulation population,
            Random random
    ) {
        if (population.authors().isEmpty()) {
            throw new IllegalArgumentException("Seed population must contain authors");
        }
        List<SeedVideoProfile> videos = new ArrayList<>(profile.videoCount());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < profile.videoCount(); i++) {
            SeedAuthorProfile author = population.authors().get(i % population.authors().size());
            InterestCluster cluster = snapshot.requireCluster(author.clusterKey());
            LocalDateTime publishedAt = now
                    .minusDays(random.nextInt(Math.max(1, profile.recentDaysWindow())))
                    .minusHours(random.nextInt(24))
                    .minusMinutes(random.nextInt(60));
            validateCluster(cluster);
            List<Long> tagIds = pickTags(cluster, random);
            Long categoryId = pickCategory(cluster, random);
            boolean editorial = random.nextDouble() < EDITORIAL_PROBABILITY;
            double potentialScore = clampScore(baseScore(author.tier()) + random.nextDouble() * 0.12D + (editorial ? 0.04D : 0.0D));
            boolean recommended = isRecommended(author.tier(), potentialScore, editorial);

            Video video = new Video();
            video.setTitle(buildTitle(cluster, i, random));
            video.setDescription(buildDescription(cluster, author.username(), editorial, i));
            video.setAuthorId(author.userId());
            video.setCoverUrl("seed://cover/" + author.userId() + "/" + (i + 1) + ".jpg");
            video.setPreviewUrl("seed://preview/" + author.userId() + "/" + (i + 1) + ".webp");
            video.setVideoUrl("seed://video/" + author.userId() + "/" + (i + 1) + ".mp4");
            video.setPlayCount(0L);
            video.setLikeCount(0L);
            video.setSaveCount(0L);
            video.setDurationSeconds(120 + random.nextInt(20) * 30);
            video.setIsRecommended(recommended);
            video.setCategoryId(categoryId);
            video.setCreateTime(publishedAt);
            videoMapper.insert(video);

            for (Long tagId : tagIds) {
                VideoTag videoTag = new VideoTag();
                videoTag.setVideoId(video.getId());
                videoTag.setTagId(tagId);
                videoTag.setCreateTime(publishedAt);
                videoTagMapper.insert(videoTag);
            }

            recommendationFeatureService.syncVideoTagFeatures(
                    video.getId(),
                    tagIds,
                    FEATURE_SOURCE,
                    FEATURE_VERSION,
                    buildConfidenceMap(tagIds)
            );

            videos.add(new SeedVideoProfile(
                    video.getId(),
                    author.userId(),
                    cluster.key(),
                    categoryId,
                    tagIds,
                    editorial,
                    potentialScore,
                    publishedAt
            ));
        }
        return videos;
    }

    private String buildTitle(InterestCluster cluster, int index, Random random) {
        String prefix = cluster.titlePrefixes().get(random.nextInt(cluster.titlePrefixes().size()));
        String keyword = cluster.titleKeywords().get(random.nextInt(cluster.titleKeywords().size()));
        return prefix + " · " + keyword + " 第" + (index + 1) + "期";
    }

    private String buildDescription(InterestCluster cluster, String authorName, boolean editorial, int index) {
        return cluster.displayName() + " 向 seed 视频 #" + (index + 1)
                + "，作者 " + authorName
                + (editorial ? "，含精选加权" : "，用于推荐冷启动与标签特征验证");
    }

    private void validateCluster(InterestCluster cluster) {
        requireNonEmpty(cluster.categoryIds(), "categoryIds");
        requireNonEmpty(cluster.tagIds(), "tagIds");
        requireNonBlank(cluster.titlePrefixes(), "titlePrefixes");
        requireNonBlank(cluster.titleKeywords(), "titleKeywords");
    }

    private Long pickCategory(InterestCluster cluster, Random random) {
        List<Long> categoryIds = cluster.categoryIds();
        return categoryIds.get(random.nextInt(categoryIds.size()));
    }

    private boolean isRecommended(SeedAuthorProfile.AuthorTier tier, double potentialScore, boolean editorial) {
        if (editorial) {
            return true;
        }
        return potentialScore >= recommendationThreshold(tier);
    }

    private double recommendationThreshold(SeedAuthorProfile.AuthorTier tier) {
        return switch (tier) {
            case HEAD -> HEAD_RECOMMENDATION_THRESHOLD;
            case MID -> MID_RECOMMENDATION_THRESHOLD;
            case TAIL -> TAIL_RECOMMENDATION_THRESHOLD;
        };
    }

    private void requireNonEmpty(List<?> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Cluster " + fieldName + " must not be empty");
        }
    }

    private void requireNonBlank(List<String> values, String fieldName) {
        requireNonEmpty(values, fieldName);
        boolean hasBlank = values.stream().anyMatch(value -> value == null || value.isBlank());
        if (hasBlank) {
            throw new IllegalArgumentException("Cluster " + fieldName + " must not contain blank values");
        }
    }

    private List<Long> pickTags(InterestCluster cluster, Random random) {
        int maxTagCount = Math.min(4, cluster.tagIds().size());
        int minTagCount = Math.min(2, maxTagCount);
        int targetCount = minTagCount;
        if (maxTagCount > minTagCount) {
            targetCount += random.nextInt(maxTagCount - minTagCount + 1);
        }
        Set<Long> selected = new LinkedHashSet<>();
        while (selected.size() < targetCount) {
            selected.add(cluster.tagIds().get(random.nextInt(cluster.tagIds().size())));
        }
        return List.copyOf(selected);
    }

    private Map<Long, Double> buildConfidenceMap(List<Long> tagIds) {
        Map<Long, Double> confidenceByTagId = new LinkedHashMap<>();
        for (Long tagId : tagIds) {
            confidenceByTagId.put(tagId, TAG_CONFIDENCE);
        }
        return confidenceByTagId;
    }

    private double baseScore(SeedAuthorProfile.AuthorTier tier) {
        return switch (tier) {
            case HEAD -> HEAD_BASE_SCORE;
            case MID -> MID_BASE_SCORE;
            case TAIL -> TAIL_BASE_SCORE;
        };
    }

    private double clampScore(double score) {
        return Math.max(0.0D, Math.min(1.0D, score));
    }
}