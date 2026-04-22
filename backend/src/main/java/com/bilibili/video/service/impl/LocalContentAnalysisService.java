package com.bilibili.video.service.impl;

import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.client.dto.ScoredTag;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.model.vo.TagVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalContentAnalysisService {

    private static final String DEFAULT_TITLE = "未命名视频";
    private static final double DEFAULT_MATCH_CONFIDENCE = 0.6D;
    private static final double DEFAULT_FALLBACK_CONFIDENCE = 0.45D;
    private static final List<CategoryKeywordRule> CATEGORY_KEYWORD_RULES = List.of(
            new CategoryKeywordRule(
                    List.of("java", "springboot", "springcloud", "vue", "react", "mysql", "redis", "docker", "linux", "git", "kafka", "nginx", "elasticsearch", "netty"),
                    List.of("编程开发", "科技")
            ),
            new CategoryKeywordRule(
                    List.of("人工智能", "机器学习", "算法"),
                    List.of("人工智能", "科技")
            ),
            new CategoryKeywordRule(
                    List.of("数码", "开箱", "评测", "测评"),
                    List.of("数码评测", "科技")
            ),
            new CategoryKeywordRule(
                    List.of("学习方法"),
                    List.of("学习方法", "知识")
            ),
            new CategoryKeywordRule(
                    List.of("科普"),
                    List.of("科普", "知识")
            ),
            new CategoryKeywordRule(
                    List.of("nba", "篮球", "足球"),
                    List.of("体育")
            )
    );

    public List<Long> recommendTagIds(String title, String description, List<TagVO> allTags) {
        if (allTags == null || allTags.isEmpty()) {
            return Collections.emptyList();
        }
        String source = normalize(title) + " " + normalize(description);
        Set<Long> matchedIds = new LinkedHashSet<>();
        for (TagVO tag : allTags) {
            if (tag == null || tag.getId() == null || isBlank(tag.getName())) {
                continue;
            }
            if (containsTag(source, tag.getName())) {
                matchedIds.add(tag.getId());
            }
        }
        return new ArrayList<>(matchedIds);
    }

    public ContentAnalysisResult analyzeContent(String title, String description, List<Tag> allTags, List<Category> allCategories) {
        String normalizedTitle = safeTrim(title);
        String normalizedDescription = safeTrim(description);
        String source = (normalizedTitle + " " + normalizedDescription).trim();

        List<Tag> matchedTags = matchTags(source, allTags);
        List<Long> suggestedTagIds = matchedTags.stream().map(Tag::getId).filter(Objects::nonNull).toList();

        ContentAnalysisResult result = new ContentAnalysisResult();
        result.setSuggestedTags(matchedTags.stream().map(Tag::getName).toList());
        result.setTagScores(buildTagScores(matchedTags));
        result.setSummary(normalizedDescription);

        Long categoryId = resolveCategoryId(matchedTags, source, allCategories);
        if (categoryId != null) {
            result.setSuggestedCategoryId(categoryId.intValue());
            result.setSuggestedCategoryName(resolveCategoryName(categoryId, allCategories));
        }

        result.setGeneratedTitle(buildGeneratedTitle(normalizedTitle, normalizedDescription, matchedTags, suggestedTagIds, allTags));
        return result;
    }

    public Map<Long, Double> buildConfidenceMap(List<Long> tagIds, ContentAnalysisResult result, List<Tag> allTags) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> confidenceById = new LinkedHashMap<>();
        Map<String, Long> tagIdByName = allTags == null ? Collections.emptyMap() : allTags.stream()
                .filter(tag -> tag != null && tag.getId() != null && !isBlank(tag.getName()))
                .collect(Collectors.toMap(tag -> normalize(tag.getName()), Tag::getId, (a, b) -> a, LinkedHashMap::new));

        if (result != null && result.getTagScores() != null) {
            for (ScoredTag scoredTag : result.getTagScores()) {
                if (scoredTag == null || isBlank(scoredTag.getTag())) {
                    continue;
                }
                Long tagId = tagIdByName.get(normalize(scoredTag.getTag()));
                if (tagId != null) {
                    confidenceById.put(tagId, clamp(scoredTag.getConfidence() == null ? DEFAULT_MATCH_CONFIDENCE : scoredTag.getConfidence()));
                }
            }
        }

        Map<Long, Double> output = new LinkedHashMap<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                output.put(tagId, confidenceById.getOrDefault(tagId, DEFAULT_FALLBACK_CONFIDENCE));
            }
        }
        return output;
    }

    public Long resolveCategoryId(List<Tag> matchedTags, String source, List<Category> allCategories) {
        Long categoryId = resolveCategoryIdByTags(matchedTags, allCategories);
        if (categoryId != null) {
            return categoryId;
        }
        Long textCategoryId = resolveCategoryIdByText(normalize(source), allCategories);
        if (textCategoryId != null) {
            return textCategoryId;
        }
        return findCategoryIdByPreferredNames(allCategories, List.of("科技", "知识", "体育"));
    }

    private List<Tag> matchTags(String source, List<Tag> allTags) {
        if (isBlank(source) || allTags == null || allTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tag> matched = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Tag tag : allTags) {
            if (tag == null || tag.getId() == null || isBlank(tag.getName())) {
                continue;
            }
            if (containsTag(source, tag.getName()) && seen.add(tag.getId())) {
                matched.add(tag);
            }
        }
        return matched;
    }

    private List<ScoredTag> buildTagScores(List<Tag> matchedTags) {
        if (matchedTags == null || matchedTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScoredTag> scores = new ArrayList<>();
        for (Tag tag : matchedTags) {
            ScoredTag scoredTag = new ScoredTag();
            scoredTag.setTag(tag.getName());
            scoredTag.setConfidence(DEFAULT_MATCH_CONFIDENCE);
            scoredTag.setReason("matched_by_keyword");
            scores.add(scoredTag);
        }
        return scores;
    }

    private String buildGeneratedTitle(String title, String description, List<Tag> matchedTags, List<Long> suggestedTagIds, List<Tag> allTags) {
        if (!isBlank(title)) {
            return title;
        }
        if (!matchedTags.isEmpty()) {
            List<String> names = matchedTags.stream().map(Tag::getName).limit(2).toList();
            if (names.contains("篮球") || names.contains("NBA")) {
                return "NBA篮球精彩片段";
            }
            if (names.contains("足球")) {
                return "足球赛事精彩片段";
            }
            return String.join("·", names) + "精彩分享";
        }
        if (!isBlank(description)) {
            return description.length() > 30 ? description.substring(0, 30) : description;
        }
        List<String> namesFromIds = resolveTagNames(suggestedTagIds, allTags);
        if (!namesFromIds.isEmpty()) {
            return String.join("·", namesFromIds.stream().limit(2).toList()) + "精彩分享";
        }
        return DEFAULT_TITLE;
    }

    private List<String> resolveTagNames(List<Long> tagIds, List<Tag> allTags) {
        if (tagIds == null || tagIds.isEmpty() || allTags == null || allTags.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> nameById = allTags.stream()
                .filter(tag -> tag != null && tag.getId() != null && !isBlank(tag.getName()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a, LinkedHashMap::new));
        List<String> names = new ArrayList<>();
        for (Long tagId : tagIds) {
            String name = nameById.get(tagId);
            if (!isBlank(name) && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private Long resolveCategoryIdByTags(List<Tag> matchedTags, List<Category> allCategories) {
        if (matchedTags == null || matchedTags.isEmpty()) {
            return null;
        }
        Set<String> matchedTagNames = matchedTags.stream()
                .filter(Objects::nonNull)
                .map(Tag::getName)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (CategoryKeywordRule rule : CATEGORY_KEYWORD_RULES) {
            if (rule.matchesAnyTag(matchedTagNames)) {
                return findCategoryIdByPreferredNames(allCategories, rule.preferredCategoryNames());
            }
        }
        return null;
    }

    private Long resolveCategoryIdByText(String source, List<Category> allCategories) {
        if (isBlank(source)) {
            return null;
        }
        return resolveCategoryIdByTextContent(normalize(source), allCategories);
    }

    private Long resolveCategoryIdByTextContent(String normalizedContent, List<Category> allCategories) {
        if (isBlank(normalizedContent)) {
            return null;
        }
        for (CategoryKeywordRule rule : CATEGORY_KEYWORD_RULES) {
            if (rule.matchesText(normalizedContent)) {
                return findCategoryIdByPreferredNames(allCategories, rule.preferredCategoryNames());
            }
        }
        return null;
    }

    private String resolveCategoryName(Long categoryId, List<Category> allCategories) {
        if (categoryId == null || allCategories == null) {
            return null;
        }
        for (Category category : allCategories) {
            if (category != null && Objects.equals(category.getId(), categoryId)) {
                return category.getName();
            }
        }
        return null;
    }

    private Long findCategoryIdByPreferredNames(List<Category> allCategories, List<String> preferredNames) {
        if (allCategories == null || allCategories.isEmpty()) {
            return null;
        }
        for (String preferredName : preferredNames) {
            for (Category category : allCategories) {
                if (category != null && category.getId() != null && preferredName.equals(category.getName())) {
                    return category.getId();
                }
            }
        }
        return null;
    }

    private boolean containsTag(String source, String tagName) {
        return !isBlank(source) && !isBlank(tagName) && normalize(source).contains(normalize(tagName));
    }

    private boolean containsAny(String source, List<String> keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record CategoryKeywordRule(List<String> keywords, List<String> preferredCategoryNames) {

        private CategoryKeywordRule {
            keywords = keywords.stream().map(LocalContentAnalysisService::normalizeKeyword).toList();
        }

        private boolean matchesAnyTag(Set<String> normalizedTagNames) {
            return keywords.stream().anyMatch(normalizedTagNames::contains);
        }

        private boolean matchesTag(String normalizedContent) {
            return matchesText(normalizedContent);
        }

        private boolean matchesText(String normalizedContent) {
            return containsAnyNormalized(normalizedContent, keywords);
        }
    }

    private static boolean containsAnyNormalized(String source, List<String> keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private double clamp(Double confidence) {
        if (confidence == null) {
            return DEFAULT_MATCH_CONFIDENCE;
        }
        if (confidence < 0D) {
            return 0D;
        }
        if (confidence > 1D) {
            return 1D;
        }
        return confidence;
    }
}
