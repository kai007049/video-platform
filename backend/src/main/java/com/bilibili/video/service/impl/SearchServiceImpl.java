package com.bilibili.video.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.User;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.vo.SearchUserVO;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.search.VideoDocument;
import com.bilibili.video.search.VideoSearchService;
import com.bilibili.video.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    @Override
    public IPage<VideoVO> searchVideos(String keyword, int page, int size, String sortBy) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return new Page<>(page, size, 0);
        }

        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String normalizedSortBy = normalizeSortBy(sortBy);
        boolean cacheEligible = isSearchResultCacheEligible(normalizedKeyword, safePage, safeSize);

        if (cacheEligible) {
            CachedSearchResult cached = getCachedSearchResult(normalizedKeyword, safePage, safeSize, normalizedSortBy);
            if (cached != null) {
                return buildSearchPageFromIds(cached.getIds(), cached.getTotal() == null ? 0L : cached.getTotal(), safePage, safeSize, normalizedSortBy);
            }
        }

        Query query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch(m -> m
                        .fields("title", "description")
                        .query(normalizedKeyword)
                ))
                .withPageable(PageRequest.of(safePage - 1, safeSize))
                .build();

        SearchHits<VideoDocument> result = elasticsearchOperations.search(query, VideoDocument.class);
        List<Long> ids = result.stream()
                .map(hit -> hit.getContent().getId())
                .filter(Objects::nonNull)
                .toList();

        if (cacheEligible) {
            cacheSearchResult(normalizedKeyword, safePage, safeSize, normalizedSortBy,
                    new CachedSearchResult(ids, result.getTotalHits(), Instant.now().getEpochSecond()));
        }

        return buildSearchPageFromIds(ids, result.getTotalHits(), safePage, safeSize, normalizedSortBy);
    }

    @Override
    public List<SearchUserVO> searchUsers(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword)
                .orderByDesc(User::getCreateTime)
                .last("limit " + offset + "," + safeSize));

        return users.stream().map(u -> {
            SearchUserVO vo = new SearchUserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setAvatar(u.getAvatar());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void indexVideo(Long videoId) {
        if (videoId == null) return;
        Video video = videoMapper.selectById(videoId);
        if (video == null) return;
        videoSearchService.index(video);
    }

    @Override
    public void deleteVideo(Long videoId) {
        videoSearchService.delete(videoId);
    }

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

    @Override
    public void recordSearchKeyword(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String normalized = keyword.trim();

        String hotBucketKey = getHotSearchBucketKey(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(hotBucketKey, normalized, 1D);
        redisTemplate.expire(hotBucketKey, RedisConstants.HOT_SEARCH_BUCKET_TTL);

        if (userId != null) {
            String key = RedisConstants.SEARCH_HISTORY_KEY_PREFIX + userId;
            redisTemplate.opsForList().remove(key, 0, normalized);
            redisTemplate.opsForList().leftPush(key, normalized);
            redisTemplate.opsForList().trim(key, 0, 29);
            redisTemplate.expire(key, RedisConstants.SEARCH_HISTORY_TTL);
        }
    }

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

    @Override
    public void clearSearchHistory(Long userId) {
        if (userId == null) {
            return;
        }
        String key = RedisConstants.SEARCH_HISTORY_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    @Override
    public List<String> getHotSearches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<String> keys = getRecentHotSearchBucketKeys();
        if (keys.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> existingKeys = keys.stream()
                .filter(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)))
                .collect(Collectors.toList());
        if (existingKeys.isEmpty()) {
            return new ArrayList<>();
        }

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
        List<Video> videos = loadVideosByIds(ids);
        List<VideoVO> records = videos.isEmpty() ? List.of() : videoViewAssembler.toVideoVOList(videos, userId);

        Page<VideoVO> pageResult = new Page<>(safePage, safeSize, result.getTotalHits());
        pageResult.setRecords(records);
        return pageResult;
    }

    private IPage<VideoVO> buildSearchPageFromIds(List<Long> ids, long total, int page, int size, String sortBy) {
        List<Video> videos = loadVideosByIds(ids);
        List<VideoVO> records = videos.isEmpty() ? List.of() : videoViewAssembler.toVideoVOList(videos, null);
        if ("save".equalsIgnoreCase(sortBy)) {
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

    private List<Video> loadVideosByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Video> videoMap = videoMapper.selectBatchIds(ids).stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(Video::getId, video -> video, (left, right) -> left, LinkedHashMap::new));
        return ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isSearchResultCacheEligible(String normalizedKeyword, int page, int size) {
        return page == 1
                && CACHEABLE_PAGE_SIZES.contains(size)
                && normalizedKeyword.length() >= MIN_CACHEABLE_QUERY_LENGTH
                && normalizedKeyword.length() <= MAX_CACHEABLE_QUERY_LENGTH
                && isMeaningfulQuery(normalizedKeyword);
    }

    private boolean isMeaningfulQuery(String normalizedKeyword) {
        return normalizedKeyword != null
                && !normalizedKeyword.isBlank()
                && normalizedKeyword.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "comprehensive";
        }
        return sortBy.trim().toLowerCase(Locale.ROOT);
    }

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

    private void cacheSearchResult(String normalizedKeyword, int page, int size, String sortBy, CachedSearchResult result) {
        if (result == null) {
            return;
        }
        Duration ttl = (result.getIds() == null || result.getIds().isEmpty())
                ? RedisConstants.SEARCH_RESULT_EMPTY_TTL
                : RedisConstants.SEARCH_RESULT_TTL;
        redisTemplate.opsForValue().set(buildSearchResultKey(normalizedKeyword, page, size, sortBy), result, ttl);
    }

    private String buildSearchResultKey(String normalizedKeyword, int page, int size, String sortBy) {
        return RedisConstants.SEARCH_RESULT_KEY_PREFIX
                + normalizedKeyword.replace(' ', '_')
                + ":sort:" + sortBy
                + ":page:" + page
                + ":size:" + size
                + ":v1";
    }

    private String getHotSearchBucketKey(LocalDate date) {
        return RedisConstants.HOT_SEARCH_KEY_PREFIX + HOT_SEARCH_BUCKET_FORMATTER.format(date);
    }

    private List<String> getRecentHotSearchBucketKeys() {
        List<String> keys = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < RedisConstants.HOT_SEARCH_WINDOW_DAYS; i++) {
            keys.add(getHotSearchBucketKey(today.minusDays(i)));
        }
        return keys;
    }
}
