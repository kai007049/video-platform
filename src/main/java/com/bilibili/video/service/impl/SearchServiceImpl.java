package com.bilibili.video.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final String SEARCH_HISTORY_KEY_PREFIX = "search:history:";
    private static final String HOT_SEARCH_KEY = "search:hot";

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final VideoSearchService videoSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public IPage<VideoVO> searchVideos(String keyword, int page, int size, String sortBy) {
        if (keyword == null || keyword.isBlank()) {
            return new Page<>(page, size, 0);
        }
        Query query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch(m -> m
                        .fields("title", "description")
                        .query(keyword)
                ))
                .withPageable(PageRequest.of(Math.max(0, page - 1), Math.min(50, size)))
                .build();

        SearchHits<VideoDocument> result = elasticsearchOperations.search(query, VideoDocument.class);
        List<VideoVO> records = new ArrayList<>();
        for (SearchHit<VideoDocument> hit : result) {
            VideoDocument doc = hit.getContent();
            Video video = videoMapper.selectById(doc.getId());
            if (video != null) {
                VideoVO vo = new VideoVO();
                vo.setId(video.getId());
                vo.setTitle(video.getTitle());
                vo.setDescription(video.getDescription());
                vo.setAuthorId(video.getAuthorId());
                vo.setCoverUrl(video.getCoverUrl());
                vo.setPreviewUrl(video.getPreviewUrl());
                vo.setVideoUrl(video.getVideoUrl());
                vo.setPlayCount(video.getPlayCount());
                vo.setLikeCount(video.getLikeCount());
                vo.setSaveCount(video.getSaveCount());
                if (video.getSaveCount() != null && "save".equalsIgnoreCase(sortBy)) {
                    // saveCount 无 ES 字段，补充内存排序在后面统一处理
                }
                vo.setDurationSeconds(video.getDurationSeconds());
                vo.setCategoryId(video.getCategoryId());
                vo.setCreateTime(video.getCreateTime());
                records.add(vo);
            }
        }
        if ("save".equalsIgnoreCase(sortBy)) {
            records.sort((a, b) -> Long.compare(
                    b.getSaveCount() == null ? 0L : b.getSaveCount(),
                    a.getSaveCount() == null ? 0L : a.getSaveCount()
            ));
        }

        Page<VideoVO> resultPage = new Page<>(page, size, result.getTotalHits());
        resultPage.setRecords(records);
        return resultPage;
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
    public void recordSearchKeyword(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String normalized = keyword.trim();

        // 全站热搜计数
        redisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, normalized, 1D);

        // 登录用户记录个人搜索历史（最近在前）
        if (userId != null) {
            String key = SEARCH_HISTORY_KEY_PREFIX + userId;
            // 先删再加，保证最新搜索排在最前
            redisTemplate.opsForList().remove(key, 0, normalized);
            redisTemplate.opsForList().leftPush(key, normalized);
            // 仅保留最近 30 条
            redisTemplate.opsForList().trim(key, 0, 29);
        }
    }

    @Override
    public List<String> getSearchHistory(Long userId, int limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(limit, 30));
        String key = SEARCH_HISTORY_KEY_PREFIX + userId;
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
        String key = SEARCH_HISTORY_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    @Override
    public List<String> getHotSearches(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Set<Object> values = redisTemplate.opsForZSet().reverseRange(HOT_SEARCH_KEY, 0, safeLimit - 1);
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream().map(String::valueOf).collect(Collectors.toList());
    }
}
