package com.bilibili.video.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.VideoMapper;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final VideoMapper videoMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final VideoSearchService videoSearchService;

    @Override
    public IPage<VideoVO> searchVideos(String keyword, int page, int size) {
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
                vo.setDurationSeconds(video.getDurationSeconds());
                vo.setCategoryId(video.getCategoryId());
                vo.setCreateTime(video.getCreateTime());
                records.add(vo);
            }
        }
        Page<VideoVO> resultPage = new Page<>(page, size, result.getTotalHits());
        resultPage.setRecords(records);
        return resultPage;
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
}
