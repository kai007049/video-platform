package com.bilibili.video.service.impl;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.mapper.CommentMapper;
import com.bilibili.video.mapper.DanmuMapper;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.TagMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagFeatureMapper;
import com.bilibili.video.mapper.VideoTagMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.RecommendationFeatureService;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.utils.MinioUtils;
import com.bilibili.video.utils.VideoCoverExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VideoCommandServiceTest {

    @Test
    void upload_shouldRejectMissingManualCategoryBeforeUploadingFile() {
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        VideoCommandService service = createService(categoryMapper, tagMapper);
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setTitle("手动投稿标题");
        dto.setDescription("手动投稿简介");
        dto.setCategoryId(999L);
        dto.setTagIds(List.of(1L));

        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.upload(videoFile(), null, dto, 123L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分类不存在");

        verifyNoInteractions(serviceMinio(service));
    }

    @Test
    void upload_shouldRejectMissingManualTagBeforeUploadingFile() {
        CategoryMapper categoryMapper = mock(CategoryMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        VideoCommandService service = createService(categoryMapper, tagMapper);
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setTitle("手动投稿标题");
        dto.setDescription("手动投稿简介");
        dto.setCategoryId(10L);
        dto.setTagIds(List.of(1L, 2L));

        Category category = new Category();
        category.setId(10L);
        when(categoryMapper.selectById(10L)).thenReturn(category);
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("Java");
        when(tagMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(tag));

        assertThatThrownBy(() -> service.upload(videoFile(), null, dto, 123L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在");

        verifyNoInteractions(serviceMinio(service));
    }

    private VideoCommandService createService(CategoryMapper categoryMapper, TagMapper tagMapper) {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagMapper videoTagMapper = mock(VideoTagMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        DanmuMapper danmuMapper = mock(DanmuMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        LocalContentAnalysisService localContentAnalysisService = mock(LocalContentAnalysisService.class);
        MinioUtils minioUtils = mock(MinioUtils.class);
        VideoCoverExtractor videoCoverExtractor = mock(VideoCoverExtractor.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        VideoCacheService videoCacheService = mock(VideoCacheService.class);
        MQService mqService = mock(MQService.class);
        VideoViewAssembler videoViewAssembler = mock(VideoViewAssembler.class);
        VideoPostProcessFallbackService videoPostProcessFallbackService = mock(VideoPostProcessFallbackService.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);

        return new VideoCommandService(
                videoMapper,
                videoTagMapper,
                videoTagFeatureMapper,
                videoLikeMapper,
                favoriteMapper,
                commentMapper,
                danmuMapper,
                watchHistoryMapper,
                tagMapper,
                categoryMapper,
                localContentAnalysisService,
                minioUtils,
                videoCoverExtractor,
                redisTemplate,
                videoCacheService,
                mqService,
                videoViewAssembler,
                videoPostProcessFallbackService,
                recommendationFeatureService
        );
    }

    private MinioUtils serviceMinio(VideoCommandService service) {
        try {
            var field = VideoCommandService.class.getDeclaredField("minioUtils");
            field.setAccessible(true);
            return (MinioUtils) field.get(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MockMultipartFile videoFile() {
        return new MockMultipartFile("video", "demo.mp4", "video/mp4", "video-content".getBytes());
    }
}
