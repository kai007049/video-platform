package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.entity.Favorite;
import com.kai.videoplatform.entity.Tag;
import com.kai.videoplatform.entity.VideoLike;
import com.kai.videoplatform.entity.WatchHistory;
import com.kai.videoplatform.mapper.FavoriteMapper;
import com.kai.videoplatform.mapper.TagMapper;
import com.kai.videoplatform.mapper.VideoLikeMapper;
import com.kai.videoplatform.mapper.WatchHistoryMapper;
import com.kai.videoplatform.service.RecommendationFeatureService;
import com.kai.videoplatform.service.UserProfileSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileSummaryServiceImpl implements UserProfileSummaryService {

    private final RecommendationFeatureService recommendationFeatureService;
    private final TagMapper tagMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;

    @Override
    public String buildProfileSummary(Long userId) {
        if (userId == null) {
            return "anonymous user, no profile available";
        }

        // 1) 从已有 user_interest_tag 画像中提取高权重兴趣标签。
        List<Long> topTagIds = recommendationFeatureService.listTopInterestTagIds(userId, 5);
        Map<Long, String> tagNameMap = listTagNameMap(topTagIds);
        String topTags = topTagIds.stream()
                .map(tagNameMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        if (topTags.isBlank()) {
            topTags = "none";
        }

        // 2) 统计最近显式行为信号（观看/点赞/收藏），后续可继续扩展。
        long recentWatchCount = countRecentWatchRecords(userId, 20);
        long recentLikeCount = countRecentLikes(userId, 20);
        long recentFavoriteCount = countRecentFavorites(userId, 20);

        // 3) 组装紧凑画像上下文，供后端推荐与规则逻辑使用。
        return "userId=" + userId
                + "; topTags=" + topTags
                + "; recentWatchCount=" + recentWatchCount
                + "; recentLikeCount=" + recentLikeCount
                + "; recentFavoriteCount=" + recentFavoriteCount
                + "; scene=homepage_recommend";
    }

    @Override
    public List<Long> listRecentWatchedVideoIds(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }
        int safeLimit = Math.min(limit, 100);
        List<WatchHistory> rows = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .orderByDesc(WatchHistory::getUpdateTime)
                .last("limit " + safeLimit));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 LinkedHashSet：既去重，又保留按时间倒序的先后顺序。
        Set<Long> uniq = new LinkedHashSet<>();
        for (WatchHistory row : rows) {
            if (row.getVideoId() != null) {
                uniq.add(row.getVideoId());
            }
        }
        return List.copyOf(uniq);
    }

    private Map<Long, String> listTagNameMap(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        return tags.stream()
                .filter(tag -> tag.getId() != null)
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
    }

    private long countRecentWatchRecords(Long userId, int limit) {
        List<WatchHistory> rows = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .orderByDesc(WatchHistory::getUpdateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
        return rows == null ? 0L : rows.size();
    }

    private long countRecentLikes(Long userId, int limit) {
        List<VideoLike> rows = videoLikeMapper.selectList(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getUserId, userId)
                .orderByDesc(VideoLike::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
        return rows == null ? 0L : rows.size();
    }

    private long countRecentFavorites(Long userId, int limit) {
        List<Favorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
        return rows == null ? 0L : rows.size();
    }
}