package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.VideoTagFeature;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagFeatureMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.RecExposureLogService;
import com.bilibili.video.service.RecommendationFeatureService;
import com.bilibili.video.service.RecommendationService;
import com.bilibili.video.service.UserProfileSummaryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private static final String STRATEGY_VERSION = "backend-v2";

    private final VideoMapper videoMapper;
    private final VideoTagFeatureMapper videoTagFeatureMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final FollowMapper followMapper;
    private final RecommendationFeatureService recommendationFeatureService;
    private final UserProfileSummaryService userProfileSummaryService;
    private final VideoViewAssembler videoViewAssembler;
    private final RecExposureLogService recExposureLogService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${recommend.recall.hot-size:200}")
    private int hotRecallSize;

    @Value("${recommend.recall.fresh-size:120}")
    private int freshRecallSize;

    @Value("${recommend.recall.tag-size-per-tag:80}")
    private int tagRecallSizePerTag;

    @Value("${recommend.recall.category-size-per-category:60}")
    private int categoryRecallSizePerCategory;

    @Value("${recommend.recall.author-size-per-author:40}")
    private int authorRecallSizePerAuthor;

    @Value("${recommend.weight.hot:2.6}")
    private double hotWeight;

    @Value("${recommend.weight.fresh:1.2}")
    private double freshWeight;

    @Value("${recommend.weight.tag:3.4}")
    private double tagWeight;

    @Value("${recommend.weight.category:1.8}")
    private double categoryWeight;

    @Value("${recommend.weight.author:1.6}")
    private double authorWeight;

    @Value("${recommend.weight.editorial:0.8}")
    private double editorialWeight;

    @Value("${recommend.weight.recent-watch-penalty:1.4}")
    private double recentWatchPenaltyWeight;

    @Value("${recommend.rerank.max-same-author:2}")
    private int maxSameAuthorPerPage;

    @Value("${recommend.rerank.max-same-category:3}")
    private int maxSameCategoryPerPage;

    @Value("${recommend.rerank.max-consecutive-same-category:2}")
    private int maxConsecutiveSameCategory;

    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));
        int windowSize = RedisConstants.RECOMMEND_RESULT_WINDOW_SIZE;

        CachedRecommendationWindow cachedWindow = getCachedWindow(userId, windowSize);
        if (cachedWindow != null) {
            return buildPageFromCachedWindow(cachedWindow, safePage, safeSize, userId);
        }

        String lockKey = buildRecommendLockKey(userId, windowSize);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.RECOMMEND_RESULT_LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                CachedRecommendationWindow rebuilt = buildRecommendationWindow(userId, windowSize);
                cacheRecommendationWindow(userId, windowSize, rebuilt);
                return buildPageFromCachedWindow(rebuilt, safePage, safeSize, userId);
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CachedRecommendationWindow retryWindow = getCachedWindow(userId, windowSize);
        if (retryWindow != null) {
            return buildPageFromCachedWindow(retryWindow, safePage, safeSize, userId);
        }

        CachedRecommendationWindow fallbackWindow = buildRecommendationWindow(userId, windowSize);
        return buildPageFromCachedWindow(fallbackWindow, safePage, safeSize, userId);
    }

    private CachedRecommendationWindow buildRecommendationWindow(Long userId, int windowSize) {
        Map<Long, RecommendationCandidate> candidateMap = new LinkedHashMap<>();
        List<Long> recentWatchedIds = userId == null
                ? Collections.emptyList()
                : userProfileSummaryService.listRecentWatchedVideoIds(userId, 30);

        recallFromHot(candidateMap);
        recallFromFresh(candidateMap);
        recallFromEditorial(candidateMap);
        if (userId != null) {
            recallFromUserInterest(candidateMap, userId);
            recallFromCategoryAffinity(candidateMap, userId);
            recallFromAuthorAffinity(candidateMap, userId);
        }

        List<Long> candidateIds = candidateMap.keySet().stream().toList();
        if (candidateIds.isEmpty()) {
            return fallbackRecommendationWindow(windowSize);
        }

        Map<Long, Video> videoMap = loadVideoMap(candidateIds);
        enrichVideoMetadata(candidateMap, videoMap);
        applyRecentWatchPenalty(candidateMap, recentWatchedIds);
        scoreCandidates(candidateMap);

        List<RecommendationCandidate> rankedCandidates = candidateMap.values().stream()
                .filter(item -> item.video != null)
                .sorted(Comparator.comparingDouble(RecommendationCandidate::getFinalScore).reversed())
                .toList();
        if (rankedCandidates.isEmpty()) {
            return fallbackRecommendationWindow(windowSize);
        }

        List<RecommendationCandidate> rerankedCandidates = rerankCandidates(rankedCandidates, recentWatchedIds);
        List<RecommendationCandidate> windowCandidates = rerankedCandidates.stream()
                .limit(windowSize)
                .toList();
        boolean hasMore = rerankedCandidates.size() > windowCandidates.size();
        return buildCachedWindow(windowCandidates, hasMore);
    }

    private CachedRecommendationWindow fallbackRecommendationWindow(int windowSize) {
        Map<Long, RecommendationCandidate> candidateMap = new LinkedHashMap<>();
        recallFromHot(candidateMap);
        recallFromFresh(candidateMap);
        recallFromEditorial(candidateMap);
        List<Long> candidateIds = candidateMap.keySet().stream().toList();
        if (candidateIds.isEmpty()) {
            return new CachedRecommendationWindow(Collections.emptyList(), 0, false, Instant.now().getEpochSecond(), Collections.emptyMap());
        }
        Map<Long, Video> videoMap = loadVideoMap(candidateIds);
        enrichVideoMetadata(candidateMap, videoMap);
        scoreCandidates(candidateMap);
        List<RecommendationCandidate> ranked = candidateMap.values().stream()
                .filter(item -> item.video != null)
                .sorted(Comparator.comparingDouble(RecommendationCandidate::getFinalScore).reversed())
                .toList();
        List<RecommendationCandidate> windowCandidates = ranked.stream().limit(windowSize).toList();
        boolean hasMore = ranked.size() > windowCandidates.size();
        return buildCachedWindow(windowCandidates, hasMore);
    }

    private CachedRecommendationWindow buildCachedWindow(List<RecommendationCandidate> candidates, boolean hasMore) {
        List<Long> ids = candidates.stream()
                .map(RecommendationCandidate::getVideoId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, CachedRecommendationMeta> meta = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            RecommendationCandidate candidate = candidates.get(i);
            if (candidate == null || candidate.getVideoId() == null) {
                continue;
            }
            CachedRecommendationMeta item = new CachedRecommendationMeta();
            item.setScore(candidate.getFinalScore());
            item.setChannels(candidate.getRecallChannels().stream().sorted().toList());
            item.setRank(i + 1);
            meta.put(String.valueOf(candidate.getVideoId()), item);
        }
        return new CachedRecommendationWindow(ids, ids.size(), hasMore, Instant.now().getEpochSecond(), meta);
    }

    private IPage<VideoVO> buildPageFromCachedWindow(CachedRecommendationWindow window, int page, int size, Long userId) {
        if (window == null || window.getIds() == null || window.getIds().isEmpty()) {
            Page<VideoVO> empty = new Page<>(page, size, 0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        int from = Math.max(page - 1, 0) * Math.max(size, 1);
        if (from >= window.getIds().size()) {
            return new Page<>(page, size, window.getWindowSize() == null ? 0 : window.getWindowSize());
        }
        int to = Math.min(from + size, window.getIds().size());
        List<Long> pageIds = window.getIds().subList(from, to);
        List<Video> pageVideos = loadVideosByIds(pageIds);
        List<VideoVO> records = videoViewAssembler.toVideoVOList(pageVideos, userId);
        Page<VideoVO> result = new Page<>(page, size, window.getWindowSize() == null ? records.size() : window.getWindowSize());
        result.setRecords(records);
        recExposureLogService.logRecommendationExposureFromVideos(
                userId,
                "recommended",
                page,
                size,
                records,
                toMetaByVideoId(window),
                STRATEGY_VERSION
        );
        return result;
    }

    private Map<Long, CachedRecommendationMeta> toMetaByVideoId(CachedRecommendationWindow window) {
        if (window == null || window.getMeta() == null || window.getMeta().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CachedRecommendationMeta> result = new HashMap<>();
        for (Map.Entry<String, CachedRecommendationMeta> entry : window.getMeta().entrySet()) {
            try {
                result.put(Long.valueOf(entry.getKey()), entry.getValue());
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    private CachedRecommendationWindow getCachedWindow(Long userId, int windowSize) {
        String key = buildRecommendWindowKey(userId, windowSize);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof CachedRecommendationWindow window) {
                return window;
            }
            return null;
        } catch (Exception e) {
            log.warn("recommendation cache deserialize failed, evict corrupted key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    private void cacheRecommendationWindow(Long userId, int windowSize, CachedRecommendationWindow window) {
        if (window == null) {
            return;
        }
        Duration ttl = userId == null
                ? withJitter(RedisConstants.RECOMMEND_GUEST_WINDOW_TTL)
                : withJitter(RedisConstants.RECOMMEND_USER_WINDOW_TTL);
        redisTemplate.opsForValue().set(buildRecommendWindowKey(userId, windowSize), window, ttl);
    }

    private Duration withJitter(Duration base) {
        long seconds = base.toSeconds();
        long jitter = Math.max(1L, Math.round(seconds * 0.2D));
        long extra = ThreadLocalRandom.current().nextLong(0, jitter + 1);
        return base.plusSeconds(extra);
    }

    private String buildRecommendWindowKey(Long userId, int windowSize) {
        if (userId == null) {
            return RedisConstants.RECOMMEND_RESULT_KEY_PREFIX + "guest:home:v1:window:" + windowSize;
        }
        return RedisConstants.RECOMMEND_RESULT_KEY_PREFIX + "user:" + userId + ":home:v1:window:" + windowSize;
    }

    private String buildRecommendLockKey(Long userId, int windowSize) {
        if (userId == null) {
            return RedisConstants.RECOMMEND_RESULT_LOCK_PREFIX + "guest:home:v1:window:" + windowSize;
        }
        return RedisConstants.RECOMMEND_RESULT_LOCK_PREFIX + "user:" + userId + ":home:v1:window:" + windowSize;
    }

    /**
     * 热门召回：适合兜底和冷启动，是 feed 的稳定基底。
     */
    private void recallFromHot(Map<Long, RecommendationCandidate> candidateMap) {
        String hotKey = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        Set<Object> rawIds = redisTemplate.opsForZSet().reverseRange(hotKey, 0, Math.max(0, hotRecallSize - 1));
        if (rawIds == null || rawIds.isEmpty()) {
            return;
        }
        int rank = 0;
        for (Object idObj : rawIds) {
            rank++;
            Long videoId = parseLong(idObj);
            if (videoId == null) {
                continue;
            }
            RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, videoId);
            candidate.hotRank = rank;
            candidate.hotScore = normalizedRankScore(rank);
            candidate.recallChannels.add("hot");
        }
    }

    /**
     * 新鲜召回：保证新视频能进入候选池，解决老热视频长期霸榜问题。
     */
    private void recallFromFresh(Map<Long, RecommendationCandidate> candidateMap) {
        int limit = Math.max(1, Math.min(freshRecallSize, 1000));
        List<Video> freshVideos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .orderByDesc(Video::getCreateTime)
                .last("limit " + limit));
        for (int i = 0; i < freshVideos.size(); i++) {
            Video video = freshVideos.get(i);
            if (video.getId() == null) {
                continue;
            }
            RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, video.getId());
            candidate.video = video;
            candidate.freshRank = i + 1;
            candidate.freshScore = normalizedRankScore(i + 1);
            if (Boolean.TRUE.equals(video.getIsRecommended())) {
                candidate.editorialBoost = Math.max(candidate.editorialBoost, 1.0D);
            }
            candidate.recallChannels.add("fresh");
        }
    }

    /**
     * 运营推荐召回：在冷启动或内容探索阶段为运营位保留曝光机会。
     */
    private void recallFromEditorial(Map<Long, RecommendationCandidate> candidateMap) {
        List<Video> editorialVideos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getIsRecommended, true)
                .orderByDesc(Video::getCreateTime)
                .last("limit 100"));
        for (int i = 0; i < editorialVideos.size(); i++) {
            Video video = editorialVideos.get(i);
            if (video.getId() == null) {
                continue;
            }
            RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, video.getId());
            candidate.video = video;
            candidate.editorialBoost = Math.max(candidate.editorialBoost, normalizedRankScore(i + 1));
            candidate.recallChannels.add("editorial");
        }
    }

    /**
     * 标签兴趣召回：使用用户兴趣标签画像与视频标签特征做匹配，不再只看裸 tag 关系。
     */
    private void recallFromUserInterest(Map<Long, RecommendationCandidate> candidateMap, Long userId) {
        List<Long> topTagIds = recommendationFeatureService.listTopInterestTagIds(userId, 12);
        if (topTagIds.isEmpty()) {
            return;
        }
        int tagPosition = 0;
        for (Long tagId : topTagIds) {
            tagPosition++;
            int limit = Math.max(1, Math.min(tagRecallSizePerTag, 300));
            List<VideoTagFeature> rows = videoTagFeatureMapper.selectList(new LambdaQueryWrapper<VideoTagFeature>()
                    .eq(VideoTagFeature::getTagId, tagId)
                    .orderByDesc(VideoTagFeature::getConfidence)
                    .last("limit " + limit));
            for (int i = 0; i < rows.size(); i++) {
                VideoTagFeature row = rows.get(i);
                if (row.getVideoId() == null) {
                    continue;
                }
                RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, row.getVideoId());
                double confidence = row.getConfidence() == null ? 0.6D : row.getConfidence();
                double score = (1.0D / tagPosition) * confidence * normalizedRankScore(i + 1);
                candidate.interestTagScore += score;
                candidate.recallChannels.add("tag");
            }
        }
    }

    /**
     * 分类偏好召回：当 tag 信号不足时，分类偏好是比较稳的第二层兴趣兜底。
     */
    private void recallFromCategoryAffinity(Map<Long, RecommendationCandidate> candidateMap, Long userId) {
        List<Long> topCategoryIds = listTopCategoryIds(userId, 4);
        if (topCategoryIds.isEmpty()) {
            return;
        }
        for (int position = 0; position < topCategoryIds.size(); position++) {
            Long categoryId = topCategoryIds.get(position);
            List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                    .eq(Video::getCategoryId, categoryId)
                    .orderByDesc(Video::getCreateTime)
                    .last("limit " + Math.max(1, Math.min(categoryRecallSizePerCategory, 200))));
            for (int i = 0; i < videos.size(); i++) {
                Video video = videos.get(i);
                if (video.getId() == null) {
                    continue;
                }
                RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, video.getId());
                candidate.video = candidate.video == null ? video : candidate.video;
                candidate.categoryAffinityScore += (1.0D / (position + 1)) * normalizedRankScore(i + 1);
                candidate.recallChannels.add("category");
            }
        }
    }

    /**
     * 作者偏好召回：贴近视频平台常见使用习惯，用户往往会持续消费固定创作者内容。
     */
    private void recallFromAuthorAffinity(Map<Long, RecommendationCandidate> candidateMap, Long userId) {
        List<Long> topAuthorIds = listTopAuthorIds(userId, 4);
        if (topAuthorIds.isEmpty()) {
            return;
        }
        for (int position = 0; position < topAuthorIds.size(); position++) {
            Long authorId = topAuthorIds.get(position);
            List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                    .eq(Video::getAuthorId, authorId)
                    .orderByDesc(Video::getCreateTime)
                    .last("limit " + Math.max(1, Math.min(authorRecallSizePerAuthor, 120))));
            for (int i = 0; i < videos.size(); i++) {
                Video video = videos.get(i);
                if (video.getId() == null) {
                    continue;
                }
                RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, video.getId());
                candidate.video = candidate.video == null ? video : candidate.video;
                candidate.authorAffinityScore += (1.0D / (position + 1)) * normalizedRankScore(i + 1);
                candidate.recallChannels.add("author");
            }
        }
    }

    /**
     * 最近看过的内容做惩罚，避免刚看完又被推到首页前列。
     */
    private void applyRecentWatchPenalty(Map<Long, RecommendationCandidate> candidateMap, List<Long> recentWatchedIds) {
        if (recentWatchedIds == null || recentWatchedIds.isEmpty()) {
            return;
        }
        Set<Long> recentSet = new HashSet<>(recentWatchedIds);
        for (Long videoId : recentSet) {
            RecommendationCandidate candidate = candidateMap.get(videoId);
            if (candidate != null) {
                candidate.recentWatchedPenalty = 1.0D;
            }
        }
    }

    /**
     * 统一打分：将多路召回产生的特征融合成可解释的最终分值。
     */
    private void scoreCandidates(Map<Long, RecommendationCandidate> candidateMap) {
        LocalDateTime now = LocalDateTime.now();
        for (RecommendationCandidate candidate : candidateMap.values()) {
            if (candidate.video == null) {
                continue;
            }
            candidate.videoAgeHours = candidate.video.getCreateTime() == null
                    ? 9999D
                    : Math.max(0D, Duration.between(candidate.video.getCreateTime(), now).toMinutes() / 60.0D);
            double freshnessDecay = 1.0D / (1.0D + candidate.videoAgeHours / 24.0D);
            double recentWatchPenalty = candidate.recentWatchedPenalty > 0 ? recentWatchPenaltyWeight : 0D;
            candidate.finalScore = hotWeight * candidate.hotScore
                    + freshWeight * candidate.freshScore * freshnessDecay
                    + tagWeight * candidate.interestTagScore
                    + categoryWeight * candidate.categoryAffinityScore
                    + authorWeight * candidate.authorAffinityScore
                    + editorialWeight * candidate.editorialBoost
                    - recentWatchPenalty;
        }
    }

    /**
     * 页面级重排：在最终页内控制作者/分类重复，避免首页观感过于单一。
     */
    private List<RecommendationCandidate> rerankCandidates(List<RecommendationCandidate> rankedCandidates, List<Long> recentWatchedIds) {
        List<RecommendationCandidate> out = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        Map<Long, Integer> categoryCount = new HashMap<>();
        Long previousCategoryId = null;
        int consecutiveCategoryCount = 0;

        for (RecommendationCandidate candidate : rankedCandidates) {
            if (candidate.video == null || candidate.video.getId() == null) {
                continue;
            }
            Long authorId = candidate.video.getAuthorId();
            Long categoryId = candidate.video.getCategoryId();
            if (authorId != null && authorCount.getOrDefault(authorId, 0) >= maxSameAuthorPerPage) {
                continue;
            }
            if (categoryId != null && categoryCount.getOrDefault(categoryId, 0) >= maxSameCategoryPerPage) {
                continue;
            }
            if (categoryId != null && Objects.equals(previousCategoryId, categoryId) && consecutiveCategoryCount >= maxConsecutiveSameCategory) {
                continue;
            }
            out.add(candidate);
            if (authorId != null) {
                authorCount.put(authorId, authorCount.getOrDefault(authorId, 0) + 1);
            }
            if (categoryId != null) {
                categoryCount.put(categoryId, categoryCount.getOrDefault(categoryId, 0) + 1);
            }
            if (Objects.equals(previousCategoryId, categoryId)) {
                consecutiveCategoryCount++;
            } else {
                previousCategoryId = categoryId;
                consecutiveCategoryCount = 1;
            }
        }

        if (out.isEmpty()) {
            return rankedCandidates;
        }
        Set<Long> selectedIds = out.stream().map(item -> item.video.getId()).collect(Collectors.toCollection(HashSet::new));
        for (RecommendationCandidate candidate : rankedCandidates) {
            if (candidate.video != null && selectedIds.add(candidate.video.getId())) {
                out.add(candidate);
            }
        }
        return out;
    }

    private RecommendationCandidate getOrCreateCandidate(Map<Long, RecommendationCandidate> candidateMap, Long videoId) {
        return candidateMap.computeIfAbsent(videoId, RecommendationCandidate::new);
    }

    private Map<Long, Video> loadVideoMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return videoMapper.selectBatchIds(ids).stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(Video::getId, video -> video, (a, b) -> a, LinkedHashMap::new));
    }

    private List<Video> loadVideosByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Video> videoMap = loadVideoMap(ids);
        return ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void enrichVideoMetadata(Map<Long, RecommendationCandidate> candidateMap, Map<Long, Video> videoMap) {
        for (RecommendationCandidate candidate : candidateMap.values()) {
            if (candidate.video == null) {
                candidate.video = videoMap.get(candidate.videoId);
            }
        }
    }

    private List<Long> listTopCategoryIds(Long userId, int limit) {
        Map<Long, Double> scoreByCategory = new HashMap<>();
        mergeCategoryScoreFromWatchHistory(scoreByCategory, userId);
        mergeCategoryScoreFromLikes(scoreByCategory, userId, 3.0D);
        mergeCategoryScoreFromFavorites(scoreByCategory, userId, 3.5D);
        return scoreByCategory.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void mergeCategoryScoreFromWatchHistory(Map<Long, Double> scoreByCategory, Long userId) {
        List<WatchHistory> histories = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .orderByDesc(WatchHistory::getUpdateTime)
                .last("limit 100"));
        if (histories.isEmpty()) {
            return;
        }
        Map<Long, Video> videoMap = loadVideoMap(histories.stream().map(WatchHistory::getVideoId).filter(Objects::nonNull).toList());
        for (WatchHistory history : histories) {
            Video video = videoMap.get(history.getVideoId());
            if (video == null || video.getCategoryId() == null) {
                continue;
            }
            double score = Math.max(0.2D, Math.min((history.getWatchSeconds() == null ? 0 : history.getWatchSeconds()) / 120.0D, 2.0D));
            scoreByCategory.put(video.getCategoryId(), scoreByCategory.getOrDefault(video.getCategoryId(), 0D) + score);
        }
    }

    private void mergeCategoryScoreFromLikes(Map<Long, Double> scoreByCategory, Long userId, double actionWeight) {
        List<VideoLike> likes = videoLikeMapper.selectList(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getUserId, userId)
                .orderByDesc(VideoLike::getCreateTime)
                .last("limit 60"));
        mergeCategoryScoreByVideoIds(scoreByCategory, likes.stream().map(VideoLike::getVideoId).toList(), actionWeight);
    }

    private void mergeCategoryScoreFromFavorites(Map<Long, Double> scoreByCategory, Long userId, double actionWeight) {
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit 60"));
        mergeCategoryScoreByVideoIds(scoreByCategory, favorites.stream().map(Favorite::getVideoId).toList(), actionWeight);
    }

    private void mergeCategoryScoreByVideoIds(Map<Long, Double> scoreByCategory, List<Long> videoIds, double actionWeight) {
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        Map<Long, Video> videoMap = loadVideoMap(videoIds.stream().filter(Objects::nonNull).toList());
        for (Long videoId : videoIds) {
            Video video = videoMap.get(videoId);
            if (video == null || video.getCategoryId() == null) {
                continue;
            }
            scoreByCategory.put(video.getCategoryId(), scoreByCategory.getOrDefault(video.getCategoryId(), 0D) + actionWeight);
        }
    }

    private List<Long> listTopAuthorIds(Long userId, int limit) {
        Map<Long, Double> scoreByAuthor = new HashMap<>();
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .last("limit 80"));
        for (Follow follow : follows) {
            if (follow.getFollowingId() != null) {
                scoreByAuthor.put(follow.getFollowingId(), scoreByAuthor.getOrDefault(follow.getFollowingId(), 0D) + 5.0D);
            }
        }
        List<WatchHistory> histories = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .orderByDesc(WatchHistory::getUpdateTime)
                .last("limit 80"));
        Map<Long, Video> videoMap = loadVideoMap(histories.stream().map(WatchHistory::getVideoId).filter(Objects::nonNull).toList());
        for (WatchHistory history : histories) {
            Video video = videoMap.get(history.getVideoId());
            if (video == null || video.getAuthorId() == null) {
                continue;
            }
            double score = Math.max(0.2D, Math.min((history.getWatchSeconds() == null ? 0 : history.getWatchSeconds()) / 180.0D, 1.5D));
            scoreByAuthor.put(video.getAuthorId(), scoreByAuthor.getOrDefault(video.getAuthorId(), 0D) + score);
        }
        List<VideoLike> likes = videoLikeMapper.selectList(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getUserId, userId)
                .orderByDesc(VideoLike::getCreateTime)
                .last("limit 40"));
        mergeAuthorScoreByVideoIds(scoreByAuthor, likes.stream().map(VideoLike::getVideoId).toList(), 3.0D);
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit 40"));
        mergeAuthorScoreByVideoIds(scoreByAuthor, favorites.stream().map(Favorite::getVideoId).toList(), 3.5D);
        return scoreByAuthor.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void mergeAuthorScoreByVideoIds(Map<Long, Double> scoreByAuthor, List<Long> videoIds, double actionWeight) {
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        Map<Long, Video> videoMap = loadVideoMap(videoIds.stream().filter(Objects::nonNull).toList());
        for (Long videoId : videoIds) {
            Video video = videoMap.get(videoId);
            if (video == null || video.getAuthorId() == null) {
                continue;
            }
            scoreByAuthor.put(video.getAuthorId(), scoreByAuthor.getOrDefault(video.getAuthorId(), 0D) + actionWeight);
        }
    }

    private double normalizedRankScore(int rank) {
        return rank <= 0 ? 0D : 1.0D / rank;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    @Data
    public static class RecommendationCandidate {
        private final Long videoId;
        private final Set<String> recallChannels = new LinkedHashSet<>();
        private Video video;
        private int hotRank;
        private double hotScore;
        private int freshRank;
        private double freshScore;
        private double interestTagScore;
        private double categoryAffinityScore;
        private double authorAffinityScore;
        private double editorialBoost;
        private double recentWatchedPenalty;
        private double videoAgeHours;
        private double finalScore;

        public RecommendationCandidate(Long videoId) {
            this.videoId = videoId;
        }
    }
}
