package com.bilibili.video.search.impl;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.User;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.search.VideoDocument;
import com.bilibili.video.search.VideoSearchRepository;
import com.bilibili.video.search.VideoSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "video.search", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class VideoSearchServiceImpl implements VideoSearchService {

    private final VideoSearchRepository repository;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public void index(Video video) {
        if (video == null || video.getId() == null) return;
        VideoDocument doc = new VideoDocument();
        doc.setId(video.getId());
        doc.setTitle(video.getTitle());
        doc.setDescription(video.getDescription());
        doc.setUploaderId(video.getAuthorId());

        User author = userMapper.selectById(video.getAuthorId());
        if (author != null) {
            doc.setUploaderName(author.getUsername());
        }

        String categoryName = null;
        if (video.getCategoryId() != null && video.getCategoryId() > 0) {
            Category c = categoryMapper.selectById(video.getCategoryId());
            if (c != null) categoryName = c.getName();
        }
        doc.setCategory(categoryName);

        doc.setCreateTime(video.getCreateTime());
        doc.setViews(video.getPlayCount() == null ? 0L : video.getPlayCount());
        doc.setLikes(video.getLikeCount() == null ? 0L : video.getLikeCount());

        repository.save(doc);
    }

    @Override
    public void delete(Long videoId) {
        if (videoId == null) return;
        repository.deleteById(videoId);
    }
}
