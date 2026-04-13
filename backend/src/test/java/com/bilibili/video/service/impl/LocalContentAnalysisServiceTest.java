package com.bilibili.video.service.impl;

import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.client.dto.ScoredTag;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.model.vo.TagVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContentAnalysisServiceTest {

    private final LocalContentAnalysisService service = new LocalContentAnalysisService();

    @Test
    void recommendTagIds_shouldMatchTitleTagsAndDeduplicate() {
        List<TagVO> allTags = List.of(
                tagVo(1L, "Java"),
                tagVo(2L, "SpringBoot"),
                tagVo(3L, "NBA")
        );

        List<Long> result = service.recommendTagIds("Java SpringBoot Java 实战", "", allTags);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    void analyzeContent_shouldReturnCategoryAndGeneratedTitleWhenDescriptionMissing() {
        List<Tag> allTags = List.of(
                tag(1L, "Java"),
                tag(2L, "SpringBoot"),
                tag(3L, "篮球")
        );
        List<Category> allCategories = List.of(
                category(10L, "体育"),
                category(20L, "科技"),
                category(30L, "知识")
        );

        ContentAnalysisResult result = service.analyzeContent("Java 项目实战", null, allTags, allCategories);

        assertThat(result.getSuggestedTags()).containsExactly("Java");
        assertThat(result.getSuggestedCategoryId()).isEqualTo(20);
        assertThat(result.getSuggestedCategoryName()).isEqualTo("科技");
        assertThat(result.getGeneratedTitle()).isEqualTo("Java 项目实战");
        assertThat(result.getSummary()).isEmpty();
        assertThat(result.getTagScores())
                .extracting(ScoredTag::getTag, ScoredTag::getConfidence)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Java", 0.6D));

        Map<Long, Double> confidenceMap = service.buildConfidenceMap(List.of(1L, 2L), result, allTags);
        assertThat(confidenceMap).containsExactlyEntriesOf(Map.of(1L, 0.6D, 2L, 0.45D));
    }

    @Test
    void analyzeContent_shouldReturnSuggestedTagsCategoryGeneratedTitleAndTagScores() {
        List<Tag> allTags = List.of(
                tag(1L, "Vue"),
                tag(2L, "React"),
                tag(3L, "篮球")
        );
        List<Category> allCategories = List.of(
                category(10L, "体育"),
                category(20L, "数码"),
                category(30L, "科技")
        );

        ContentAnalysisResult result = service.analyzeContent(
                "Vue React 组件设计",
                "使用 Vue 和 React 对比前端组件模式",
                allTags,
                allCategories
        );

        assertThat(result.getSuggestedTags()).containsExactly("Vue", "React");
        assertThat(result.getSuggestedCategoryId()).isEqualTo(20);
        assertThat(result.getGeneratedTitle()).isEqualTo("Vue React 组件设计");
        assertThat(result.getTagScores())
                .extracting(ScoredTag::getTag, ScoredTag::getConfidence)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Vue", 0.6D),
                        org.assertj.core.groups.Tuple.tuple("React", 0.6D)
                );

        Map<Long, Double> confidenceMap = service.buildConfidenceMap(List.of(1L, 2L), result, allTags);
        assertThat(confidenceMap).containsExactlyEntriesOf(Map.of(1L, 0.6D, 2L, 0.6D));
    }

    @Test
    void buildConfidenceMap_shouldUseFallbackConfidenceForTagsWithoutScores() {
        List<Tag> allTags = List.of(
                tag(1L, "Java"),
                tag(2L, "SpringBoot")
        );

        Map<Long, Double> confidenceMap = service.buildConfidenceMap(List.of(1L, 2L), null, allTags);

        assertThat(confidenceMap).containsExactlyEntriesOf(Map.of(1L, 0.45D, 2L, 0.45D));
    }

    private static TagVO tagVo(Long id, String name) {
        TagVO tagVO = new TagVO();
        tagVO.setId(id);
        tagVO.setName(name);
        return tagVO;
    }

    private static Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
