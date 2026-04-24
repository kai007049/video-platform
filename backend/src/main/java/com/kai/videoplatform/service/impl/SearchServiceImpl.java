package com.kai.videoplatform.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.vo.SearchUserVO;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.search.VideoDocument;
import com.kai.videoplatform.search.VideoSearchService;
import com.kai.videoplatform.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 搜索服务实现：
 * 负责视频搜索、用户搜索、搜索结果缓存、搜索历史以及热搜统计。
 */
public class SearchServiceImpl implements SearchService {

    private static final DateTimeFormatter HOT_SEARCH_BUCKET_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<Integer> CACHEABLE_PAGE_SIZES = Set.of(10, 12, 20);
    private static final int MIN_CACHEABLE_QUERY_LENGTH = 2;
    private static final int MAX_CACHEABLE_QUERY_LENGTH = 20;

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final VideoSearchService videoSearchService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoViewAssembler videoViewAssembler;

    /**
     * 搜索视频。
     * 先尝试命中搜索结果缓存，未命中时走 Elasticsearch，并按需回填缓存。
     */
    @Override
    public IPage<VideoVO> searchVideos(String keyword, int page, int size, String sortBy) {
        // 先把关键词做归一化，避免大小写、空格差异导致同义查询重复回源。
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return new Page<>(page, size, 0);
        }

        // 统一收敛分页和排序参数，避免异常值放大查询范围。
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String normalizedSortBy = normalizeSortBy(sortBy);
        // 只有第一页、常见 pageSize、长度合适且有实际意义的关键词才值得缓存。
        boolean cacheEligible = isSearchResultCacheEligible(normalizedKeyword, safePage, safeSize);

        if (cacheEligible) {
            // 优先读缓存，缓存命中后只需要按 id 回表取视频并组装返回。
            CachedSearchResult cached = getCachedSearchResult(normalizedKeyword, safePage, safeSize, normalizedSortBy);
            if (cached != null) {
                return buildSearchPageFromIds(cached.getIds(), cached.getTotal() == null ? 0L : cached.getTotal(), safePage, safeSize, normalizedSortBy);
            }
        }

        // 缓存未命中时，走 Elasticsearch 做全文检索。
        Query query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch(m -> m
                        .fields("title", "description")
                        .query(normalizedKeyword)
                ))
                .withPageable(PageRequest.of(safePage - 1, safeSize))
                .build();

        SearchHits<VideoDocument> result = elasticsearchOperations.search(query, VideoDocument.class);
        // 先只取出 ES 命中的视频 id，后续再按 id 保序回表查数据库。
        List<Long> ids = result.stream()
                .map(hit -> hit.getContent().getId())
                .filter(Objects::nonNull)
                .toList();

        if (cacheEligible) {
            // 缓存中只保存 id 列表和 total，不直接缓存完整 VideoVO，减小缓存体积。
            cacheSearchResult(normalizedKeyword, safePage, safeSize, normalizedSortBy,
                    new CachedSearchResult(ids, result.getTotalHits(), Instant.now().getEpochSecond()));
        }

        return buildSearchPageFromIds(ids, result.getTotalHits(), safePage, safeSize, normalizedSortBy);
    }

    /**
     * 搜索用户。
     * 当前走数据库 like 查询，适合体量较小时的用户名搜索场景。
     */
    @Override
    public List<SearchUserVO> searchUsers(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;

        // 直接在用户名上做 like，并按注册时间倒序返回。
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword)
                .orderByDesc(User::getCreateTime)
                .last("limit " + offset + "," + safeSize));

        // 只映射前端搜索结果页真正需要的字段。
        return users.stream().map(u -> {
            SearchUserVO vo = new SearchUserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setAvatar(u.getAvatar());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 将单个视频写入搜索索引。
     */
    @Override
    public void indexVideo(Long videoId) {
        if (videoId == null) return;
        // 先从数据库查出最新视频数据，再写入 ES，避免索引内容过旧。
        Video video = videoMapper.selectById(videoId);
        if (video == null) return;
        videoSearchService.index(video);
    }

    /**
     * 从搜索索引中删除单个视频。
     */
    @Override
    public void deleteVideo(Long videoId) {
        videoSearchService.delete(videoId);
    }

    /**
     * 全量重建视频搜索索引。
     * 按主键顺序遍历数据库中的全部视频并重新写入索引。
     */
    @Override
    public int reindexAllVideos() {
        List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .orderByAsc(Video::getId));
        if (videos == null || videos.isEmpty()) {
            return 0;
        }
        int indexedCount = 0;
        for (Video video : videos) {
            if (video == null || video.getId() == null) {
                continue;
            }
            videoSearchService.index(video);
            indexedCount++;
        }
        return indexedCount;
    }

    /**
     * 记录搜索关键词。
     * 同时更新热搜统计和用户个人搜索历史。
     */
    @Override
    public void recordSearchKeyword(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String normalized = keyword.trim();

        // 热搜按“天桶”统计，每个关键词在当天 zset 中累加一次分数。
        String hotBucketKey = getHotSearchBucketKey(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(hotBucketKey, normalized, 1D);
        redisTemplate.expire(hotBucketKey, RedisConstants.HOT_SEARCH_BUCKET_TTL);

        if (userId != null) {
            String key = RedisConstants.SEARCH_HISTORY_KEY_PREFIX + userId;
            // 先删掉旧记录，再左插，保证同一关键词只保留最近一次位置。
            redisTemplate.opsForList().remove(key, 0, normalized);
            redisTemplate.opsForList().leftPush(key, normalized);
            // 历史最多保留 30 条，避免列表无限增长。
            redisTemplate.opsForList().trim(key, 0, 29);
            redisTemplate.expire(key, RedisConstants.SEARCH_HISTORY_TTL);
        }
    }

    /**
     * 获取用户搜索历史。
     */
    @Override
    public List<String> getSearchHistory(Long userId, int limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(limit, 30));
        String key = RedisConstants.SEARCH_HISTORY_KEY_PREFIX + userId;
        List<Object> values = redisTemplate.opsForList().range(key, 0, safeLimit - 1);
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream().map(String::valueOf).collect(Collectors.toList());
    }

    /**
     * 清空用户搜索历史。
     */
    @Override
    public void clearSearchHistory(Long userId) {
        if (userId == null) {
            return;
        }
        String key = RedisConstants.SEARCH_HISTORY_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    /**
     * 获取热搜词。
     * 会把最近几天的日桶聚合成一个窗口热搜结果。
     */
    @Override
    public List<String> getHotSearches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<String> keys = getRecentHotSearchBucketKeys();
        if (keys.isEmpty()) {
            return new ArrayList<>();
        }

        // 先筛掉 Redis 中根本不存在的桶，避免 union 空 key。
        List<String> existingKeys = keys.stream()
                .filter(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)))
                .collect(Collectors.toList());
        if (existingKeys.isEmpty()) {
            return new ArrayList<>();
        }

        // 把最近几天的热搜桶合并成一个窗口 zset，便于统一取 TopN。
        redisTemplate.opsForZSet().unionAndStore(
                existingKeys.get(0),
                existingKeys.subList(1, existingKeys.size()),
                RedisConstants.HOT_SEARCH_WINDOW_KEY
        );
        redisTemplate.expire(RedisConstants.HOT_SEARCH_WINDOW_KEY, RedisConstants.HOT_SEARCH_WINDOW_TTL);

        Set<Object> values = redisTemplate.opsForZSet().reverseRange(RedisConstants.HOT_SEARCH_WINDOW_KEY, 0, safeLimit - 1);
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream().map(String::valueOf).collect(Collectors.toList());
    }

    /**
     * 混合搜索视频。
     * 当前实现本质上仍以 Elasticsearch 检索为主，但会补齐用户态 VideoVO 信息。
     */
    @Override
    public IPage<VideoVO> hybridSearch(String keyword, int page, int size, Long userId) {
        if (keyword == null || keyword.isBlank()) {
            return new Page<>(page, size, 0);
        }

        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        Query query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch(m -> m
                        .fields("title", "description")
                        .query(keyword)
                ))
                .withPageable(PageRequest.of(safePage - 1, safeSize))
                .build();

        SearchHits<VideoDocument> result = elasticsearchOperations.search(query, VideoDocument.class);
        List<Long> ids = result.stream()
                .map(hit -> hit.getContent().getId())
                .collect(Collectors.toList());
        // 先按 ES 顺序回表，再组装成带用户状态的 VideoVO。
        List<Video> videos = loadVideosByIds(ids);
        List<VideoVO> records = videos.isEmpty() ? List.of() : videoViewAssembler.toVideoVOList(videos, userId);

        Page<VideoVO> pageResult = new Page<>(safePage, safeSize, result.getTotalHits());
        pageResult.setRecords(records);
        return pageResult;
    }

    /**
     * 根据搜索命中的视频 id 构建分页结果。
     * 会按 id 顺序回表取视频，并在需要时补做收藏量排序。
     */
    private IPage<VideoVO> buildSearchPageFromIds(List<Long> ids, long total, int page, int size, String sortBy) {
        // 先按 ES 返回的 id 顺序回表，保证搜索相关性顺序不乱。
        List<Video> videos = loadVideosByIds(ids);
        List<VideoVO> records = videos.isEmpty() ? List.of() : videoViewAssembler.toVideoVOList(videos, null);
        if ("save".equalsIgnoreCase(sortBy)) {
            // 如果前端要求按收藏量排序，则在当前页结果内按 saveCount 再排一次。
            records = new ArrayList<>(records);
            records.sort((a, b) -> Long.compare(
                    b.getSaveCount() == null ? 0L : b.getSaveCount(),
                    a.getSaveCount() == null ? 0L : a.getSaveCount()
            ));
        }
        Page<VideoVO> resultPage = new Page<>(page, size, total);
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 按给定 id 顺序批量加载视频。
     * 避免 selectBatchIds 返回顺序不稳定导致搜索结果顺位错乱。
     */
    private List<Video> loadVideosByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 先批量查出视频，再转成 Map，最后按原 ids 顺序重组结果。
        Map<Long, Video> videoMap = videoMapper.selectBatchIds(ids).stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(Video::getId, video -> video, (left, right) -> left, LinkedHashMap::new));
        return ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 判断当前查询是否适合缓存搜索结果。
     * 只缓存收益高、命中率相对稳定的查询。
     */
    private boolean isSearchResultCacheEligible(String normalizedKeyword, int page, int size) {
        return page == 1
                && CACHEABLE_PAGE_SIZES.contains(size)
                && normalizedKeyword.length() >= MIN_CACHEABLE_QUERY_LENGTH
                && normalizedKeyword.length() <= MAX_CACHEABLE_QUERY_LENGTH
                && isMeaningfulQuery(normalizedKeyword);
    }

    /**
     * 判断关键词是否是真正有意义的查询。
     * 至少要包含字母或数字，避免纯空格、纯符号之类的噪音请求进入缓存。
     */
    private boolean isMeaningfulQuery(String normalizedKeyword) {
        return normalizedKeyword != null
                && !normalizedKeyword.isBlank()
                && normalizedKeyword.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    /**
     * 归一化搜索关键词。
     * 统一大小写并压缩多余空格，提升缓存复用率。
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /**
     * 归一化排序参数。
     */
    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "comprehensive";
        }
        return sortBy.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 读取搜索结果缓存。
     * 如果缓存反序列化失败，说明该 key 已损坏，直接删除后按未命中处理。
     */
    private CachedSearchResult getCachedSearchResult(String normalizedKeyword, int page, int size, String sortBy) {
        String key = buildSearchResultKey(normalizedKeyword, page, size, sortBy);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof CachedSearchResult result) {
                return result;
            }
            return null;
        } catch (Exception e) {
            log.warn("search result cache deserialize failed, evict corrupted key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入搜索结果缓存。
     * 空结果和非空结果使用不同 TTL，兼顾缓存收益与数据新鲜度。
     */
    private void cacheSearchResult(String normalizedKeyword, int page, int size, String sortBy, CachedSearchResult result) {
        if (result == null) {
            return;
        }
        // 空结果通常变化更快，因此 TTL 更短，避免新内容进来后长时间搜不到。
        Duration ttl = (result.getIds() == null || result.getIds().isEmpty())
                ? RedisConstants.SEARCH_RESULT_EMPTY_TTL
                : RedisConstants.SEARCH_RESULT_TTL;
        redisTemplate.opsForValue().set(buildSearchResultKey(normalizedKeyword, page, size, sortBy), result, ttl);
    }

    /**
     * 构建搜索结果缓存 key。
     * 把关键词、排序、分页参数都编码到 key 里，避免不同查询互相污染。
     */
    private String buildSearchResultKey(String normalizedKeyword, int page, int size, String sortBy) {
        return RedisConstants.SEARCH_RESULT_KEY_PREFIX
                + normalizedKeyword.replace(' ', '_')
                + ":sort:" + sortBy
                + ":page:" + page
                + ":size:" + size
                + ":v1";
    }

    /**
     * 构建某一天对应的热搜桶 key。
     */
    private String getHotSearchBucketKey(LocalDate date) {
        return RedisConstants.HOT_SEARCH_KEY_PREFIX + HOT_SEARCH_BUCKET_FORMATTER.format(date);
    }

    /**
     * 获取最近几天的热搜桶 key 列表。
     * 用于聚合最近窗口期内的热搜结果。
     */
    private List<String> getRecentHotSearchBucketKeys() {
        List<String> keys = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < RedisConstants.HOT_SEARCH_WINDOW_DAYS; i++) {
            keys.add(getHotSearchBucketKey(today.minusDays(i)));
        }
        return keys;
    }
}