package com.bilibili.video.service.impl;

import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContentAnalysisServiceTest {

    private final LocalContentAnalysisService service = new LocalContentAnalysisService();

    @Test
    void shouldSuggestProgrammingDevelopmentForJavaContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "Java SpringBoot 项目实战",
                "后端接口开发与 Redis 缓存整合",
                List.of(tag(1L, "Java"), tag(2L, "SpringBoot"), tag(3L, "Redis"), tag(4L, "教程"), tag(5L, "实战")),
                List.of(
                        category(3L, "科技", 0L),
                        category(18L, "编程开发", 3L),
                        category(19L, "人工智能", 3L),
                        category(20L, "数码评测", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("编程开发");
    }

    @Test
    void shouldSuggestAiCategoryForMachineLearningContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "机器学习入门",
                "人工智能实战案例与算法讲解",
                List.of(tag(11L, "人工智能"), tag(12L, "机器学习"), tag(13L, "算法"), tag(14L, "教程")),
                List.of(
                        category(3L, "科技", 0L),
                        category(18L, "编程开发", 3L),
                        category(19L, "人工智能", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("人工智能");
    }

    @Test
    void shouldSuggestDigitalReviewForUnboxingContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "新手机开箱评测",
                "数码开箱与性能评测",
                List.of(tag(21L, "数码"), tag(22L, "开箱"), tag(23L, "评测")),
                List.of(
                        category(3L, "科技", 0L),
                        category(20L, "数码评测", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("数码评测");
    }

    @Test
    void shouldSuggestSciencePopularizationForScienceContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "量子力学科普",
                "十分钟看懂基础物理概念与实验现象",
                List.of(tag(31L, "科普"), tag(33L, "干货")),
                List.of(
                        category(4L, "知识", 0L),
                        category(21L, "科普", 4L),
                        category(22L, "学习方法", 4L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("科普");
    }

    @Test
    void shouldPreferLearningMethodCategoryWhenLearningMethodTagMatches() {
        ContentAnalysisResult result = service.analyzeContent(
                "高效学习技巧",
                "分享可执行的学习方法和复盘框架",
                List.of(tag(41L, "学习方法"), tag(42L, "复盘")),
                List.of(
                        category(4L, "知识", 0L),
                        category(21L, "科普", 4L),
                        category(22L, "学习方法", 4L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("学习方法");
    }

    @Test
    void shouldPreferLearningMethodCategoryWhenScienceAndLearningMethodBothMatch() {
        ContentAnalysisResult result = service.analyzeContent(
                "高效学习技巧",
                "这期内容同时讲科普式拆解和学习方法复盘",
                List.of(tag(51L, "科普"), tag(52L, "学习方法"), tag(53L, "复盘")),
                List.of(
                        category(4L, "知识", 0L),
                        category(21L, "科普", 4L),
                        category(22L, "学习方法", 4L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("学习方法");
    }

    @Test
    void shouldNotFallbackToLegacyDigitalCategoryWhenNoRuleMatches() {
        Long categoryId = service.resolveCategoryId(
                List.of(),
                "",
                List.of(category(12L, "数码", 0L))
        );

        assertThat(categoryId).isNull();
    }

    @Test
    void shouldResolveProgrammingCategoryFromTagsOnly() {
        Long categoryId = service.resolveCategoryId(
                List.of(tag(41L, "Java")),
                "完全不相关的生活记录",
                List.of(
                        category(3L, "科技", 0L),
                        category(18L, "编程开发", 3L)
                )
        );

        assertThat(categoryId).isEqualTo(18L);
    }

    @Test
    void shouldResolveDigitalReviewCategoryFromTextOnly() {
        Long categoryId = service.resolveCategoryId(
                List.of(tag(51L, "旅行"), tag(52L, "随手拍")),
                "这期内容专门做新耳机开箱评测和续航测试",
                List.of(
                        category(3L, "科技", 0L),
                        category(20L, "数码评测", 3L)
                )
        );

        assertThat(categoryId).isEqualTo(20L);
    }

    private Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(parentId);
        return category;
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}