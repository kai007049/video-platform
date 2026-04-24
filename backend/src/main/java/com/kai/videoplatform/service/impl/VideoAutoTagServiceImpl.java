package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.client.dto.ContentAnalysisResult;
import com.kai.videoplatform.entity.Category;
import com.kai.videoplatform.entity.Tag;
import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.mapper.CategoryMapper;
import com.kai.videoplatform.mapper.TagMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.service.VideoAutoTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 视频自动标注服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAutoTagServiceImpl implements VideoAutoTagService {

    private final VideoMapper videoMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final LocalContentAnalysisService localContentAnalysisService;

    @Override
    public void autoTagVideo(Long videoId) {
        try {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                return;
            }

            List<Tag> allTags = tagMapper.selectList(null);
            List<Category> allCategories = categoryMapper.selectList(null);
            ContentAnalysisResult analysis = localContentAnalysisService.analyzeContent(
                    video.getTitle(),
                    video.getDescription(),
                    allTags,
                    allCategories
            );

            if (video.getCategoryId() == null && analysis.getSuggestedCategoryId() != null) {
                video.setCategoryId(Long.valueOf(analysis.getSuggestedCategoryId()));
                videoMapper.updateById(video);
                log.info("自动标注分类: videoId={}, categoryId={}", videoId, analysis.getSuggestedCategoryId());
            }

            log.info("视频自动标注完成: videoId={}, tags={}", videoId, analysis.getSuggestedTags());
        } catch (Exception e) {
            log.error("视频自动标注失败: videoId={}", videoId, e);
        }
    }
}