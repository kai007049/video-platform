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
/**
 * 推荐服务实现：
 * 负责多路召回、统一打分、结果重排以及推荐窗口缓存。
 */
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

    @Value("${recommend.result.window-size:256}")
    private int recommendResultWindowSize = 256;

    @Value("${recommend.result.max-window-size:1000}")
    private int recommendResultMaxWindowSize = 1000;

    /**
     * 获取分页推荐结果。
     * 优先读取 Redis 中的推荐窗口缓存，未命中时通过分布式锁重建窗口，避免并发回源。
     */
    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        return listRecommended(page, size, userId, Collections.emptySet());
    }

    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId, Set<Long> excludeVideoIds) {
        // 统一收敛分页参数，避免前端传入异常值后把查询窗口放得过大。
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));
        Set<Long> safeExcludeIds = excludeVideoIds == null ? Collections.emptySet() : excludeVideoIds;
        boolean refreshRequest = !safeExcludeIds.isEmpty();
        // 推荐结果不是“每页单独算一次”，而是先算一整个推荐窗口，再在窗口里分页切片。
        int windowSize = resolveRecommendationWindowSize(safeSize, safeExcludeIds);

        // 第一步：优先读缓存。
        // 如果当前用户（或游客）的推荐窗口已经算好，直接按页截取即可，不再重新召回和打分。
        CachedRecommendationWindow cachedWindow = getCachedWindow(userId, windowSize);
        if (cachedWindow != null) {
            return buildResponseFromCachedWindow(cachedWindow, safePage, safeSize, userId, safeExcludeIds, refreshRequest);
        }

        // 第二步：缓存未命中时，尝试抢“重建推荐窗口”的锁。
        // 目的是避免高并发下多个请求同时执行完整推荐流程，造成数据库和 Redis 压力暴涨。
        String lockKey = buildRecommendLockKey(userId, windowSize);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.RECOMMEND_RESULT_LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 当前请求抢到锁，负责真正构建推荐窗口。
                CachedRecommendationWindow rebuilt = buildRecommendationWindow(userId, windowSize);
                // 构建完成后写入缓存，后续请求都可以直接复用这份结果。
                cacheRecommendationWindow(userId, windowSize, rebuilt);
                // 当前请求本身也直接使用刚构建出的窗口返回结果。
                return buildResponseFromCachedWindow(rebuilt, safePage, safeSize, userId, safeExcludeIds, refreshRequest);
            } finally {
                // 无论成功还是失败，都释放锁，避免后续请求一直卡住。
                redisTemplate.delete(lockKey);
            }
        }

        // 第三步：没抢到锁，说明已经有别的请求在重建推荐窗口。
        // 这里短暂等待一下，让对方有机会把缓存写好。
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第四步：等待后再读一次缓存。
        // 大多数情况下，这里已经能读到别的请求刚刚写入的推荐窗口。
        CachedRecommendationWindow retryWindow = getCachedWindow(userId, windowSize);
        if (retryWindow != null) {
            return buildResponseFromCachedWindow(retryWindow, safePage, safeSize, userId, safeExcludeIds, refreshRequest);
        }

        // 第五步：如果极端情况下还是没有缓存，就本地直接构建一份结果兜底。
        // 这样即使锁持有方失败或超时，请求也不会直接返回空结果。
        CachedRecommendationWindow fallbackWindow = buildRecommendationWindow(userId, windowSize);
        return buildResponseFromCachedWindow(fallbackWindow, safePage, safeSize, userId, safeExcludeIds, refreshRequest);
    }

    private int resolveRecommendationWindowSize(int size, Set<Long> excludeVideoIds) {
        int safeSize = Math.max(1, size);
        int baseWindowSize = Math.max(RedisConstants.RECOMMEND_RESULT_WINDOW_SIZE, recommendResultWindowSize);
        int maxWindowSize = Math.max(baseWindowSize, recommendResultMaxWindowSize);
        int seenCount = excludeVideoIds == null ? 0 : excludeVideoIds.size();
        int requiredWindowSize = seenCount + safeSize;
        return Math.min(maxWindowSize, Math.max(baseWindowSize, requiredWindowSize));
    }

    /**
     * 构建推荐窗口。
     * 先汇总多路召回候选，再补齐视频元数据、施加惩罚、统一打分并做重排。
     */
    private CachedRecommendationWindow buildRecommendationWindow(Long userId, int windowSize) {
        // candidateMap 的 key 是 videoId。
        // 这样不同召回通道命中同一个视频时，不会重复生成候选，而是把分数累加到同一个候选对象上。
        Map<Long, RecommendationCandidate> candidateMap = new LinkedHashMap<>();
        // 读取最近观看列表，用于后续给“刚看过的视频”打惩罚，减少首页重复推荐。
        List<Long> recentWatchedIds = userId == null
                ? Collections.emptyList()
                : userProfileSummaryService.listRecentWatchedVideoIds(userId, 30);

        // 第一层：公共召回。
        // 即使用户没登录，也至少能从热门、新视频、运营推荐里拿到一批候选内容。
        recallFromHot(candidateMap);//热榜召回
        recallFromFresh(candidateMap);//新榜召回
        recallFromEditorial(candidateMap);//官方推荐召回
        if (userId != null) {
            // 第二层：个性化召回。
            // 登录用户会额外叠加兴趣标签、偏好分类、偏好作者等个性化信号。
            recallFromUserInterest(candidateMap, userId);//用户兴趣召回
            recallFromCategoryAffinity(candidateMap, userId);//分类偏好召回
            recallFromAuthorAffinity(candidateMap, userId);//作者偏好召回
        }

        // 拿到所有候选的 videoId。
        List<Long> candidateIds = candidateMap.keySet().stream().toList();
        if (candidateIds.isEmpty()) {
            // 如果所有召回通道都没有产出候选，则走兜底逻辑。
            return fallbackRecommendationWindow(windowSize);
        }

        // 有些召回通道只拿到了 videoId，没有拿到完整 Video 对象。
        // 所以这里统一批量查一次数据库，把视频元数据补齐。
        Map<Long, Video> videoMap = loadVideoMap(candidateIds);
        enrichVideoMetadata(candidateMap, videoMap);
        // 给最近看过的视频做去重惩罚，避免“刚看完又推荐”。
        applyRecentWatchPenalty(candidateMap, recentWatchedIds);
        // 把热门度、新鲜度、兴趣分、分类偏好、作者偏好等信号融合成最终分值。
        scoreCandidates(candidateMap);

        // 先按最终分数从高到低排序，得到初排结果。
        List<RecommendationCandidate> rankedCandidates = candidateMap.values().stream()
                .filter(item -> item.video != null)
                .sorted(Comparator.comparingDouble(RecommendationCandidate::getFinalScore).reversed())
                .toList();
        if (rankedCandidates.isEmpty()) {
            // 如果补齐元数据后发现候选都不可用，同样退回兜底结果。
            return fallbackRecommendationWindow(windowSize);
        }

        // 在初排结果上做“多样性重排”，避免同作者/同分类过度扎堆。
        List<RecommendationCandidate> rerankedCandidates = rerankCandidates(rankedCandidates);
        // 最终只截取前 windowSize 条作为可缓存的推荐窗口。
        List<RecommendationCandidate> windowCandidates = rerankedCandidates.stream()
                .limit(windowSize)
                .toList();
        // 记录是否还有更多候选，方便后续扩展为更深分页或连续下拉。
        boolean hasMore = rerankedCandidates.size() > windowCandidates.size();
        return buildCachedWindow(windowCandidates, hasMore);
    }

    /**
     * 兜底构建推荐窗口。
     * 当个性化召回为空或有效候选不足时，只依赖公共召回生成一份可用结果。
     */
    private CachedRecommendationWindow fallbackRecommendationWindow(int windowSize) {
        Map<Long, RecommendationCandidate> candidateMap = new LinkedHashMap<>();
        // 兜底只走最稳定的公共召回，不依赖任何用户画像。
        recallFromHot(candidateMap);
        recallFromFresh(candidateMap);
        recallFromEditorial(candidateMap);
        List<Long> candidateIds = candidateMap.keySet().stream().toList();
        if (candidateIds.isEmpty()) {
            // 连公共召回都拿不到结果时，返回一个空窗口对象，而不是返回 null。
            return new CachedRecommendationWindow(Collections.emptyList(), 0, false, Instant.now().getEpochSecond(), Collections.emptyMap());
        }
        // 兜底流程同样要补齐视频信息并统一打分。
        Map<Long, Video> videoMap = loadVideoMap(candidateIds);
        enrichVideoMetadata(candidateMap, videoMap);
        scoreCandidates(candidateMap);
        List<RecommendationCandidate> ranked = candidateMap.values().stream()
                .filter(item -> item.video != null)
                .sorted(Comparator.comparingDouble(RecommendationCandidate::getFinalScore).reversed())
                .toList();
        // 兜底结果不做更复杂的个性化处理，直接取前 windowSize 条。
        List<RecommendationCandidate> windowCandidates = ranked.stream().limit(windowSize).toList();
        boolean hasMore = ranked.size() > windowCandidates.size();
        return buildCachedWindow(windowCandidates, hasMore);
    }

    /**
     * 将候选结果转换为可缓存的推荐窗口对象。
     * 既保存视频 ID 列表，也保存曝光埋点所需的分数、召回通道和名次信息。
     */
    private CachedRecommendationWindow buildCachedWindow(List<RecommendationCandidate> candidates, boolean hasMore) {
        // 缓存里只保留 ID 列表，不直接缓存完整 VideoVO，避免缓存对象过大。
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
            // meta 保存“为什么推荐它”的解释信息，后续做曝光日志时可以直接复用。
            CachedRecommendationMeta item = new CachedRecommendationMeta();
            item.setScore(candidate.getFinalScore());
            item.setChannels(candidate.getRecallChannels().stream().sorted().toList());
            // rank 记录的是它在推荐窗口中的最终顺位，从 1 开始更符合埋点语义。
            item.setRank(i + 1);
            meta.put(String.valueOf(candidate.getVideoId()), item);
        }
        return new CachedRecommendationWindow(ids, ids.size(), hasMore, Instant.now().getEpochSecond(), meta);
    }

    private CachedRecommendationWindow applyExcludeIds(CachedRecommendationWindow window, Set<Long> excludeVideoIds) {
        if (window == null || window.getIds() == null || window.getIds().isEmpty() || excludeVideoIds == null || excludeVideoIds.isEmpty()) {
            return window;
        }
        List<Long> filteredIds = window.getIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> !excludeVideoIds.contains(id))
                .toList();
        if (filteredIds.size() == window.getIds().size()) {
            return window;
        }

        Map<String, CachedRecommendationMeta> filteredMeta = new LinkedHashMap<>();
        Map<String, CachedRecommendationMeta> originalMeta = window.getMeta() == null ? Collections.emptyMap() : window.getMeta();
        for (int i = 0; i < filteredIds.size(); i++) {
            Long id = filteredIds.get(i);
            CachedRecommendationMeta original = originalMeta.get(String.valueOf(id));
            if (original == null) {
                continue;
            }
            CachedRecommendationMeta copy = new CachedRecommendationMeta();
            copy.setScore(original.getScore());
            copy.setChannels(original.getChannels());
            copy.setRank(i + 1);
            filteredMeta.put(String.valueOf(id), copy);
        }
        return new CachedRecommendationWindow(
                filteredIds,
                filteredIds.size(),
                Boolean.FALSE,
                window.getGeneratedAt(),
                filteredMeta
        );
    }

    private IPage<VideoVO> buildResponseFromCachedWindow(CachedRecommendationWindow window,
                                                         int page,
                                                         int size,
                                                         Long userId,
                                                         Set<Long> excludeVideoIds,
                                                         boolean refreshRequest) {
        CachedRecommendationWindow effectiveWindow = refreshRequest ? applyExcludeIds(window, excludeVideoIds) : window;
        return buildPageFromCachedWindow(effectiveWindow, refreshRequest ? 1 : page, size, userId, page);
    }

    /**
     * 从缓存窗口中切出当前页数据，并补齐 VideoVO 与曝光埋点。
     */
    private IPage<VideoVO> buildPageFromCachedWindow(CachedRecommendationWindow window, int page, int size, Long userId) {
        return buildPageFromCachedWindow(window, page, size, userId, page);
    }

    private IPage<VideoVO> buildPageFromCachedWindow(CachedRecommendationWindow window, int page, int size, Long userId, int exposurePage) {
        if (window == null || window.getIds() == null || window.getIds().isEmpty()) {
            Page<VideoVO> empty = new Page<>(page, size, 0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        // 这里不是查数据库分页，而是在“已经算好的推荐窗口”中做切片。
        int from = Math.max(page - 1, 0) * Math.max(size, 1);
        if (from >= window.getIds().size()) {
            // 页码超出窗口范围时，直接返回空页，但 total 仍保留窗口大小。
            return new Page<>(page, size, window.getWindowSize() == null ? 0 : window.getWindowSize());
        }
        int to = Math.min(from + size, window.getIds().size());
        // 取出当前页对应的一段 videoId。
        List<Long> pageIds = window.getIds().subList(from, to);
        // 再按这些 id 批量加载视频，注意要保序，保证推荐顺位不乱。
        List<Video> pageVideos = loadVideosByIds(pageIds);
        // 最终再组装成带作者、点赞状态、观看进度等字段的 VideoVO。
        List<VideoVO> records = videoViewAssembler.toVideoVOList(pageVideos, userId);
        Page<VideoVO> result = new Page<>(page, size, window.getWindowSize() == null ? records.size() : window.getWindowSize());
        result.setRecords(records);
        // 返回给前端前记录一次曝光，方便后续做点击率、曝光率等推荐效果分析。
        recExposureLogService.logRecommendationExposureFromVideos(
                userId,
                "recommended",
                exposurePage,
                size,
                records,
                toMetaByVideoId(window),
                STRATEGY_VERSION
        );
        return result;
    }

    /**
     * 将缓存窗口中的字符串键元数据转换为以 videoId 为键的结构，便于曝光日志使用。
     */
    private Map<Long, CachedRecommendationMeta> toMetaByVideoId(CachedRecommendationWindow window) {
        if (window == null || window.getMeta() == null || window.getMeta().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CachedRecommendationMeta> result = new HashMap<>();
        for (Map.Entry<String, CachedRecommendationMeta> entry : window.getMeta().entrySet()) {
            try {
                // 缓存里的 key 是字符串形式的 videoId，这里转回 Long 方便后面按 videoId 查找。
                result.put(Long.valueOf(entry.getKey()), entry.getValue());
            } catch (NumberFormatException ignore) {
                // 忽略异常 key，避免单条脏数据影响整页推荐返回。
            }
        }
        return result;
    }

    /**
     * 读取推荐窗口缓存。
     * 如果反序列化失败，说明缓存内容损坏，直接删除后按未命中处理。
     */
    private CachedRecommendationWindow getCachedWindow(Long userId, int windowSize) {
        String key = buildRecommendWindowKey(userId, windowSize);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof CachedRecommendationWindow window) {
                return window;
            }
            // 类型不对也按未命中处理，避免错误缓存对象污染主流程。
            return null;
        } catch (Exception e) {
            log.warn("recommendation cache deserialize failed, evict corrupted key={}", key, e);
            // 读缓存时反序列化失败，说明这个 key 很可能已经损坏，直接删掉最安全。
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入推荐窗口缓存。
     * 匿名用户与登录用户使用不同 TTL，并统一加抖动避免同时过期。
     */
    private void cacheRecommendationWindow(Long userId, int windowSize, CachedRecommendationWindow window) {
        if (window == null) {
            return;
        }
        // 游客推荐相对通用，可以稍微共用久一点；登录用户推荐更个性化，TTL 通常更短一些。
        Duration ttl = userId == null
                ? withJitter(RedisConstants.RECOMMEND_GUEST_WINDOW_TTL)
                : withJitter(RedisConstants.RECOMMEND_USER_WINDOW_TTL);
        redisTemplate.opsForValue().set(buildRecommendWindowKey(userId, windowSize), window, ttl);
    }

    /**
     * 为基础 TTL 添加随机抖动，降低缓存雪崩风险。
     */
    private Duration withJitter(Duration base) {
        long seconds = base.toSeconds();
        // 抖动范围按基础 TTL 的 20% 计算，避免大量 key 在同一时刻一起过期。
        long jitter = Math.max(1L, Math.round(seconds * 0.2D));
        long extra = ThreadLocalRandom.current().nextLong(0, jitter + 1);
        return base.plusSeconds(extra);
    }

    /**
     * 构建推荐窗口缓存 key。
     * 匿名用户共享一份窗口，登录用户按 userId 维度隔离。
     */
    private String buildRecommendWindowKey(Long userId, int windowSize) {
        if (userId == null) {
            // 游客没有个性化画像，因此共享同一份推荐窗口缓存。
            return RedisConstants.RECOMMEND_RESULT_KEY_PREFIX + "guest:home:v1:window:" + windowSize;
        }
        // 登录用户按 userId 隔离缓存，保证每个人拿到的是自己的推荐结果。
        return RedisConstants.RECOMMEND_RESULT_KEY_PREFIX + "user:" + userId + ":home:v1:window:" + windowSize;
    }

    /**
     * 构建推荐窗口重建锁 key。
     */
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
        // 从 Redis 热榜 zset 里取出前 N 个热门视频 ID。
        String hotKey = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        Set<Object> rawIds = redisTemplate.opsForZSet().reverseRange(hotKey, 0, Math.max(0, hotRecallSize - 1));
        if (rawIds == null || rawIds.isEmpty()) {
            return;
        }
        int rank = 0;
        for (Object idObj : rawIds) {
            rank++;
            // Redis 里取出来的 id 可能是 String、Long 等类型，这里统一转成 Long。
            Long videoId = parseLong(idObj);
            if (videoId == null) {
                continue;
            }
            RecommendationCandidate candidate = getOrCreateCandidate(candidateMap, videoId);
            // hotRank 记录该视频在热榜中的名次，后续可用于解释为什么被推荐。
            candidate.hotRank = rank;
            // 热榜名次越靠前，热度分越高，这里用 1/rank 做一个简单衰减。
            candidate.hotScore = normalizedRankScore(rank);
            candidate.recallChannels.add("hot");
        }
    }

    /**
     * 新鲜召回：保证新视频能进入候选池，解决老热视频长期霸榜问题。
     */
    private void recallFromFresh(Map<Long, RecommendationCandidate> candidateMap) {
        // 取最近发布的一批视频，给新内容一个进入推荐池的机会。
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
            // fresh 召回直接查到了视频对象，因此顺手把 video 也填进去，后面就不用再补。
            candidate.video = video;
            candidate.freshRank = i + 1;
            // 越新的视频名次越靠前，因此 freshScore 也越高。
            candidate.freshScore = normalizedRankScore(i + 1);
            if (Boolean.TRUE.equals(video.getIsRecommended())) {
                // 如果运营也标记过该视频，顺便给它一个额外加成。
                candidate.editorialBoost = Math.max(candidate.editorialBoost, 1.0D);
            }
            candidate.recallChannels.add("fresh");
        }
    }

    /**
     * 运营推荐召回：在冷启动或内容探索阶段为运营位保留曝光机会。
     */
    private void recallFromEditorial(Map<Long, RecommendationCandidate> candidateMap) {
        // 查出后台人工标记为推荐的视频。
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
            // editorial 召回同样已经拿到了完整视频对象。
            candidate.video = video;
            // 越靠前的运营推荐内容，加成越高。
            candidate.editorialBoost = Math.max(candidate.editorialBoost, normalizedRankScore(i + 1));
            candidate.recallChannels.add("editorial");
        }
    }

    /**
     * 标签兴趣召回：使用用户兴趣标签画像与视频标签特征做匹配，不再只看裸 tag 关系。
     */
    private void recallFromUserInterest(Map<Long, RecommendationCandidate> candidateMap, Long userId) {
        // 先取出用户最感兴趣的一组标签。
        List<Long> topTagIds = recommendationFeatureService.listTopInterestTagIds(userId, 12);
        if (topTagIds.isEmpty()) {
            return;
        }
        int tagPosition = 0;
        for (Long tagId : topTagIds) {
            tagPosition++;
            int limit = Math.max(1, Math.min(tagRecallSizePerTag, 300));
            // 对每个兴趣标签，找出一批打上该标签且置信度较高的视频。
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
                // 如果标签置信度缺失，则给一个保守默认值，避免整条样本完全失效。
                double confidence = row.getConfidence() == null ? 0.6D : row.getConfidence();
                // 最终标签得分 = 标签位次权重 * 视频标签置信度 * 召回名次衰减。
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
        // 先算出用户最偏好的几个分类。
        List<Long> topCategoryIds = listTopCategoryIds(userId, 4);
        if (topCategoryIds.isEmpty()) {
            return;
        }
        for (int position = 0; position < topCategoryIds.size(); position++) {
            Long categoryId = topCategoryIds.get(position);
            // 每个偏好分类里取最新的一批视频进入候选池。
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
                // 分类召回直接拿到了完整视频，因此可以把 video 预先塞进候选中。
                candidate.video = candidate.video == null ? video : candidate.video;
                // 越靠前的偏好分类权重越高，分类内越新的内容分越高。
                candidate.categoryAffinityScore += (1.0D / (position + 1)) * normalizedRankScore(i + 1);
                candidate.recallChannels.add("category");
            }
        }
    }

    /**
     * 作者偏好召回：贴近视频平台常见使用习惯，用户往往会持续消费固定创作者内容。
     */
    private void recallFromAuthorAffinity(Map<Long, RecommendationCandidate> candidateMap, Long userId) {
        // 先算出用户最偏好的几个作者。
        List<Long> topAuthorIds = listTopAuthorIds(userId, 4);
        if (topAuthorIds.isEmpty()) {
            return;
        }
        for (int position = 0; position < topAuthorIds.size(); position++) {
            Long authorId = topAuthorIds.get(position);
            // 每个偏好作者下取最近发布的一批视频。
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
                // 越靠前的偏好作者贡献越大，作者最近作品优先进入候选池。
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
        // 转成 Set 只是为了去重，避免同一个 videoId 被重复打标。
        Set<Long> recentSet = new HashSet<>(recentWatchedIds);
        for (Long videoId : recentSet) {
            RecommendationCandidate candidate = candidateMap.get(videoId);
            if (candidate != null) {
                // 这里只打一个“最近看过”的标记，真正扣多少分由统一打分函数决定。
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
            // 先计算视频距离现在发布了多久，单位是小时。
            candidate.videoAgeHours = candidate.video.getCreateTime() == null
                    ? 9999D
                    : Math.max(0D, Duration.between(candidate.video.getCreateTime(), now).toMinutes() / 60.0D);
            // 新鲜度衰减：视频越老，freshScore 的作用就越弱。
            double freshnessDecay = 1.0D / (1.0D + candidate.videoAgeHours / 24.0D);
            // 最近看过的视频统一扣一个固定惩罚项。
            double recentWatchPenalty = candidate.recentWatchedPenalty > 0 ? recentWatchPenaltyWeight : 0D;
            // 把多路特征按配置权重加权求和，得到最终排序分。
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
    private List<RecommendationCandidate> rerankCandidates(List<RecommendationCandidate> rankedCandidates) {
        List<RecommendationCandidate> out = new ArrayList<>();
        // authorCount / categoryCount 分别记录当前已选结果里每个作者/分类出现了几次。
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
            // 如果某个作者已经出现太多次，就跳过，避免首页被同一作者刷屏。
            if (authorId != null && authorCount.getOrDefault(authorId, 0) >= maxSameAuthorPerPage) {
                continue;
            }
            // 如果某个分类已经出现太多次，也跳过，提升内容多样性。
            if (categoryId != null && categoryCount.getOrDefault(categoryId, 0) >= maxSameCategoryPerPage) {
                continue;
            }
            // 如果连续出现相同分类的次数过多，同样跳过，避免视觉上过于单调。
            if (categoryId != null && Objects.equals(previousCategoryId, categoryId) && consecutiveCategoryCount >= maxConsecutiveSameCategory) {
                continue;
            }
            // 满足约束后，正式选入结果集。
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
            // 如果约束过严导致一个都没选出来，就退回原始排序结果，保证有内容可返回。
            return rankedCandidates;
        }
        // 第二轮把前面因多样性规则被过滤的候选按原排序补回尾部，避免列表太短。
        Set<Long> selectedIds = out.stream().map(item -> item.video.getId()).collect(Collectors.toCollection(HashSet::new));
        for (RecommendationCandidate candidate : rankedCandidates) {
            if (candidate.video != null && selectedIds.add(candidate.video.getId())) {
                out.add(candidate);
            }
        }
        return out;
    }

    /**
     * 获取或创建候选对象，保证多路召回可以对同一视频累积特征分。
     */
    private RecommendationCandidate getOrCreateCandidate(Map<Long, RecommendationCandidate> candidateMap, Long videoId) {
        // 同一个视频可能同时被热榜、标签、作者等多个通道召回。
        // 这里统一复用同一个候选对象，避免重复创建后分数无法合并。
        return candidateMap.computeIfAbsent(videoId, RecommendationCandidate::new);
    }

    /**
     * 批量加载视频并按 videoId 建立映射，便于后续按需回填。
     */
    private Map<Long, Video> loadVideoMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量查库减少 N+1 查询，再转成 Map 方便通过 videoId 快速回填。
        return videoMapper.selectBatchIds(ids).stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(Video::getId, video -> video, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 按给定 ID 顺序加载视频列表，避免批量查询后顺序被打乱。
     */
    private List<Video> loadVideosByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Video> videoMap = loadVideoMap(ids);
        // selectBatchIds 返回结果顺序不可靠，因此这里按原 ids 顺序重新组装一遍。
        return ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 为候选项补齐视频实体。
     * 某些召回只拿到了 videoId，需要在这里统一补全视频元信息。
     */
    private void enrichVideoMetadata(Map<Long, RecommendationCandidate> candidateMap, Map<Long, Video> videoMap) {
        for (RecommendationCandidate candidate : candidateMap.values()) {
            if (candidate.video == null) {
                // 只有缺失 video 的候选才需要补，已经带 video 的不重复覆盖。
                candidate.video = videoMap.get(candidate.videoId);
            }
        }
    }

    /**
     * 计算用户最偏好的分类列表。
     * 综合观看、点赞、收藏等行为生成分类偏好分。
     */
    private List<Long> listTopCategoryIds(Long userId, int limit) {
        Map<Long, Double> scoreByCategory = new HashMap<>();
        // 观看、点赞、收藏都会往同一个分类分数字典里累计权重。
        mergeCategoryScoreFromWatchHistory(scoreByCategory, userId);
        mergeCategoryScoreFromLikes(scoreByCategory, userId, 3.0D);
        mergeCategoryScoreFromFavorites(scoreByCategory, userId, 3.5D);
        // 最后按分类得分从高到低排序，取前 limit 个分类作为偏好分类。
        return scoreByCategory.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 将观看历史信号合并到分类偏好分。
     * 观看时长越长，说明用户对该分类兴趣越强。
     */
    private void mergeCategoryScoreFromWatchHistory(Map<Long, Double> scoreByCategory, Long userId) {
        // 只取最近一段观看历史，避免过旧行为长期影响当前兴趣判断。
        List<WatchHistory> histories = watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .orderByDesc(WatchHistory::getUpdateTime)
                .last("limit 100"));
        if (histories.isEmpty()) {
            return;
        }
        // 先通过历史记录里的 videoId 批量查出视频，拿到它们所属分类。
        Map<Long, Video> videoMap = loadVideoMap(histories.stream().map(WatchHistory::getVideoId).filter(Objects::nonNull).toList());
        for (WatchHistory history : histories) {
            Video video = videoMap.get(history.getVideoId());
            if (video == null || video.getCategoryId() == null) {
                continue;
            }
            // 观看时长越长，说明兴趣越强，但这里做了上下限裁剪，避免极端值把分数拉爆。
            double score = Math.max(0.2D, Math.min((history.getWatchSeconds() == null ? 0 : history.getWatchSeconds()) / 120.0D, 2.0D));
            scoreByCategory.put(video.getCategoryId(), scoreByCategory.getOrDefault(video.getCategoryId(), 0D) + score);
        }
    }

    /**
     * 将点赞行为信号合并到分类偏好分。
     */
    private void mergeCategoryScoreFromLikes(Map<Long, Double> scoreByCategory, Long userId, double actionWeight) {
        List<VideoLike> likes = videoLikeMapper.selectList(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getUserId, userId)
                .orderByDesc(VideoLike::getCreateTime)
                .last("limit 60"));
        // 点赞本身不直接带分类，需要先转成视频，再通过视频映射到分类。
        mergeCategoryScoreByVideoIds(scoreByCategory, likes.stream().map(VideoLike::getVideoId).toList(), actionWeight);
    }

    /**
     * 将收藏行为信号合并到分类偏好分。
     */
    private void mergeCategoryScoreFromFavorites(Map<Long, Double> scoreByCategory, Long userId, double actionWeight) {
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit 60"));
        // 收藏通常比点赞更强，因此外层传入的 actionWeight 也会更高。
        mergeCategoryScoreByVideoIds(scoreByCategory, favorites.stream().map(Favorite::getVideoId).toList(), actionWeight);
    }

    /**
     * 根据视频列表把行为权重累加到对应分类。
     */
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
            // 把该行为的权重累加到视频所属分类上。
            scoreByCategory.put(video.getCategoryId(), scoreByCategory.getOrDefault(video.getCategoryId(), 0D) + actionWeight);
        }
    }

    /**
     * 计算用户最偏好的作者列表。
     * 综合关注、观看、点赞、收藏等行为生成作者偏好分。
     */
    private List<Long> listTopAuthorIds(Long userId, int limit) {
        Map<Long, Double> scoreByAuthor = new HashMap<>();
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId)
                .last("limit 80"));
        for (Follow follow : follows) {
            if (follow.getFollowingId() != null) {
                // 关注行为说明用户对作者存在显式偏好，因此给予更高基础权重。
                scoreByAuthor.put(follow.getFollowingId(), scoreByAuthor.getOrDefault(follow.getFollowingId(), 0D) + 5.0D);
            }
        }
        // 再叠加最近观看行为，判断用户最近在持续消费哪些作者的内容。
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
            // 观看越久，说明对该作者兴趣越强，但同样做了上限限制。
            double score = Math.max(0.2D, Math.min((history.getWatchSeconds() == null ? 0 : history.getWatchSeconds()) / 180.0D, 1.5D));
            scoreByAuthor.put(video.getAuthorId(), scoreByAuthor.getOrDefault(video.getAuthorId(), 0D) + score);
        }
        // 最后再叠加点赞和收藏这类更强的正反馈行为。
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
        // 返回得分最高的几个作者，作为作者召回的输入。
        return scoreByAuthor.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 根据视频列表把行为权重累加到对应作者。
     */
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
            // 把点赞/收藏等行为权重累加到视频所属作者上。
            scoreByAuthor.put(video.getAuthorId(), scoreByAuthor.getOrDefault(video.getAuthorId(), 0D) + actionWeight);
        }
    }

    /**
     * 将召回名次转换为归一化分值。
     * 名次越靠前，得分越高。
     */
    private double normalizedRankScore(int rank) {
        // rank=1 时得分最高，后续按倒数递减。
        return rank <= 0 ? 0D : 1.0D / rank;
    }

    /**
     * 将 Redis / 数据库中的对象安全转换为 Long。
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            // 容忍单条脏数据，避免影响整批召回流程。
            return null;
        }
    }

    /**
     * 推荐候选项。
     * 聚合单个视频在召回、打分、重排阶段需要的全部中间特征。
     */
    @Data
    public static class RecommendationCandidate {
        private final Long videoId;// 视频ID
        private final Set<String> recallChannels = new LinkedHashSet<>();// 召回渠道
        private Video video;// 视频对象
        private int hotRank;// 热度名次
        private double hotScore;// 热度得分
        private int freshRank;// 新鲜度名次
        private double freshScore;// 新鲜度得分
        private double interestTagScore;// 兴趣标签得分
        private double categoryAffinityScore;// 分类偏好得分
        private double authorAffinityScore;// 作者偏好得分
        private double editorialBoost;// 编导推荐得分
        private double recentWatchedPenalty;// 最近观看惩罚得分
        private double videoAgeHours;// 视频龄得分
        private double finalScore;// 最终得分

        /**
         * 基于 videoId 创建候选对象。
         * 后续多路召回会在同一个候选上持续累积特征分。
         */
        public RecommendationCandidate(Long videoId) {
            this.videoId = videoId;
        }
    }
}
