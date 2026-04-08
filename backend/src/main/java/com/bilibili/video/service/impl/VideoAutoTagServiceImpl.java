package com.bilibili.video.service.impl;

import com.bilibili.video.client.AgentClient;
import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.mapper.TagMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.service.VideoAutoTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final AgentClient agentClient;

    @Override
    public void autoTagVideo(Long videoId) {
        try {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                return;
            }

            // 获取候选标签和分类
            List<Tag> allTags = tagMapper.selectList(null);
            List<String> candidateTags = allTags.stream()
                    .map(Tag::getName)
                    .collect(Collectors.toList());

            List<Category> allCategories = categoryMapper.selectList(null);
            List<Map<String, Object>> candidateCategories = allCategories.stream()
                    .map(c -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", c.getId());
                        map.put("name", c.getName());
                        return map;
                    })
                    .collect(Collectors.toList());

            // 调用 AI 分析
            ContentAnalysisResult analysis = agentClient.analyzeContent(
                    video.getTitle(),
                    video.getDescription(),
                    video.getCoverUrl(),
                    candidateTags,
                    candidateCategories
            );

            // 自动补充分类（如果视频没有分类）
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
