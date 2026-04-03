package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.client.AgentClient;
import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.client.dto.ScoredTag;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoTag;
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
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.model.mq.VideoDeleteMessage;
import com.bilibili.video.model.mq.VideoProcessMessage;
import com.bilibili.video.model.mq.VideoSemanticIndexMessage;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.RecommendationFeatureService;
import com.bilibili.video.service.VideoCacheService;
import com.bilibili.video.utils.MinioUtils;
import com.bilibili.video.utils.VideoCoverExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCommandService {

    public static final String DEFAULT_COVER_OBJECT = "default/default-video-cover.png";
    private static final String DEFAULT_VIDEO_TITLE = "未命名视频";
    private static final String PLAY_COUNT_KEY = RedisConstants.VIDEO_STATS_KEY_PREFIX;
    private static final long PLAY_COUNT_EXPIRE = RedisConstants.VIDEO_STATS_EXPIRE_DAYS;
    private static final TimeUnit PLAY_COUNT_EXPIRE_UNIT = TimeUnit.DAYS;

    private final VideoMapper videoMapper;
    private final VideoTagMapper videoTagMapper;
    private final VideoTagFeatureMapper videoTagFeatureMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final DanmuMapper danmuMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final AgentClient agentClient;
    private final MinioUtils minioUtils;
    private final VideoCoverExtractor videoCoverExtractor;
    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoCacheService videoCacheService;
    private final MQService mqService;
    private final VideoViewAssembler videoViewAssembler;
    private final VideoPostProcessFallbackService videoPostProcessFallbackService;
    private final RecommendationFeatureService recommendationFeatureService;

    /**
     * 上传视频并创建记录。
     * 支持“轻投稿”：当分类/标签/简介缺失时，使用 AI 自动补全。
     */
    public VideoVO upload(MultipartFile videoFile, MultipartFile coverFile, VideoUploadDTO dto, Long authorId) {
        if (videoFile == null || videoFile.isEmpty()) {
            throw new BizException(400, "视频文件不能为空");
        }
        validateVideoFile(videoFile);

        String videoUrl;
        String coverUrl;
        boolean useDefaultCover = false;
        boolean metadataIncomplete = isMetadataIncomplete(dto);
        try {
            videoUrl = minioUtils.uploadVideo(videoFile);
            if (coverFile != null && !coverFile.isEmpty()) {
                coverUrl = minioUtils.uploadCover(coverFile);
            } else if (metadataIncomplete) {
                coverUrl = firstNonBlank(videoCoverExtractor.extractAndUploadCover(videoUrl), DEFAULT_COVER_OBJECT);
                useDefaultCover = DEFAULT_COVER_OBJECT.equals(coverUrl);
            } else {
                coverUrl = DEFAULT_COVER_OBJECT;
                useDefaultCover = true;
            }
        } catch (Exception e) {
            throw new BizException(500, "文件上传失败: " + e.getMessage());
        }

        UploadMetadata metadata = resolveUploadMetadata(dto, coverUrl);

        Video video = new Video();
        video.setTitle(metadata.title);
        video.setDescription(metadata.description);
        video.setAuthorId(authorId);
        video.setCoverUrl(coverUrl);
        video.setVideoUrl(videoUrl);
        video.setPlayCount(0L);
        video.setLikeCount(0L);
        video.setSaveCount(0L);
        video.setDurationSeconds(0);
        video.setIsRecommended(false);
        video.setCategoryId(metadata.categoryId);
        videoMapper.insert(video);

        saveTags(video.getId(), metadata.tagIds);
        recommendationFeatureService.syncVideoTagFeatures(
                video.getId(),
                metadata.tagIds,
                metadata.featureSource,
                "v1",
                metadata.confidenceByTagId
        );

        mqService.sendVideoProcess(new VideoProcessMessage(video.getId(), authorId));
        mqService.sendSearchSync(new SearchSyncMessage("video", video.getId(), "create"));
        mqService.sendVideoCoverProcess(new VideoProcessMessage(video.getId(), authorId));
        mqService.sendVideoSemanticIndex(new VideoSemanticIndexMessage(
                video.getId(),
                videoUrl,
                coverUrl,
                metadata.title,
                metadata.description,
                metadata.categoryId,
                resolveTagNames(metadata.tagIds)
        ));
        if (useDefaultCover) {
            videoPostProcessFallbackService.triggerCoverProcessFallback(video.getId());
        }
        incrHotScore(video.getId(), Constants.HOT_WEIGHT_PLAY);

        return videoViewAssembler.toVideoVO(video, authorId);
    }

    public void updateVideo(Video video) {
        videoMapper.updateById(video);
        videoCacheService.invalidateVideo(video.getId());
        mqService.sendSearchSync(new SearchSyncMessage("video", video.getId(), "update"));
    }

    public void recordPlayCount(Long videoId) {
        String key = PLAY_COUNT_KEY + videoId;
        redisTemplate.opsForHash().increment(key, RedisConstants.VIDEO_STAT_PLAY, 1);
        redisTemplate.expire(key, PLAY_COUNT_EXPIRE, PLAY_COUNT_EXPIRE_UNIT);
        incrHotScore(videoId, Constants.HOT_WEIGHT_PLAY);
        // 播放数变化会影响详情页聚合展示，这里主动失效详情缓存，确保短时间内能看到最新值。
        videoCacheService.invalidateVideo(videoId);
    }

    public void setRecommended(Long videoId, boolean recommended) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            video.setIsRecommended(recommended);
            videoMapper.updateById(video);
            videoCacheService.invalidateVideo(videoId);
        }
    }

    @Transactional
    public void deleteVideo(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        if (!video.getAuthorId().equals(userId)) {
            throw new BizException(403, "无权删除该视频");
        }

        // 先同步删除强关联业务数据，保证事务提交后数据库里不再保留 video_id 残留关系。
        deleteVideoRelatedRows(videoId);
        videoMapper.deleteById(videoId);

        // 立即清理与该视频强绑定的缓存和 Redis 状态，避免删除后仍被展示或统计任务继续消费。
        cleanupVideoRuntimeState(videoId);

        // 外部资源和索引属于最终一致性副作用，走 MQ 更适合做重试和容错。
        mqService.sendVideoDelete(new VideoDeleteMessage(videoId, video.getVideoUrl(), video.getCoverUrl()));
        mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "delete"));
    }

    /**
     * 删除视频后同步清理所有强关联表，避免孤儿数据残留。
     */
    private void deleteVideoRelatedRows(Long videoId) {
        videoTagMapper.delete(new LambdaQueryWrapper<VideoTag>()
                .eq(com.bilibili.video.entity.VideoTag::getVideoId, videoId));
        videoTagFeatureMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.VideoTagFeature>()
                .eq(com.bilibili.video.entity.VideoTagFeature::getVideoId, videoId));
        videoLikeMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.VideoLike>()
                .eq(com.bilibili.video.entity.VideoLike::getVideoId, videoId));
        favoriteMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.Favorite>()
                .eq(com.bilibili.video.entity.Favorite::getVideoId, videoId));
        commentMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.Comment>()
                .eq(com.bilibili.video.entity.Comment::getVideoId, videoId));
        danmuMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.Danmu>()
                .eq(com.bilibili.video.entity.Danmu::getVideoId, videoId));
        watchHistoryMapper.delete(new LambdaQueryWrapper<com.bilibili.video.entity.WatchHistory>()
                .eq(com.bilibili.video.entity.WatchHistory::getVideoId, videoId));
    }

    /**
     * 清理视频详情缓存、评论缓存、统计缓存和热榜成员，避免删除后仍然被读取。
     */
    private void cleanupVideoRuntimeState(Long videoId) {
        videoCacheService.invalidateVideo(videoId);
        redisTemplate.delete(RedisConstants.COMMENT_LIST_KEY_PREFIX + videoId);
        redisTemplate.delete(RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId);
        String hotKey = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().remove(hotKey, String.valueOf(videoId));
    }

    private boolean isMetadataIncomplete(VideoUploadDTO dto) {
        String title = normalizeNullableText(dto.getTitle());
        String description = normalizeNullableText(dto.getDescription());
        boolean tagsMissing = dto.getTagIds() == null || dto.getTagIds().isEmpty();
        return title.isBlank() || description.isBlank() || dto.getCategoryId() == null || tagsMissing;
    }

    /**
     * 解析投稿元数据。
     * - 全量手填：manual + confidence=1.0
     * - 存在缺失：走 AI 补全分支
     */
    private UploadMetadata resolveUploadMetadata(VideoUploadDTO dto, String coverUrl) {
        String title = normalizeNullableText(dto.getTitle());
        String description = normalizeNullableText(dto.getDescription());
        List<Long> manualTagIds = dto.getTagIds() == null ? new ArrayList<>() : dto.getTagIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Long manualCategoryId = dto.getCategoryId();

        boolean needAiFill = title.isBlank()
                || manualTagIds.isEmpty()
                || manualCategoryId == null
                || description.isBlank();

        if (!needAiFill) {
            UploadMetadata metadata = new UploadMetadata();
            metadata.title = title;
            metadata.description = description;
            metadata.categoryId = manualCategoryId;
            metadata.tagIds = manualTagIds;
            metadata.featureSource = "manual";
            metadata.confidenceByTagId = manualTagIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> 1.0D, (a, b) -> a, LinkedHashMap::new));
            return metadata;
        }

        List<Tag> allTags = tagMapper.selectList(null);
        List<Category> allCategories = categoryMapper.selectList(null);
        if (allCategories == null || allCategories.isEmpty()) {
            throw new BizException(400, "系统未配置分类，无法自动补全，请先维护分类");
        }

        List<String> candidateTags = allTags == null ? Collections.emptyList()
                : allTags.stream().map(Tag::getName).filter(Objects::nonNull).toList();
        List<Map<String, Object>> candidateCategories = allCategories.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    return m;
                })
                .toList();

        ContentAnalysisResult aiResult = agentClient.analyzeContent(
                title,
                description,
                coverUrl,
                candidateTags,
                candidateCategories
        );

        UploadMetadata metadata = new UploadMetadata();
        metadata.description = description.isBlank()
                ? firstNonBlank(aiResult == null ? null : aiResult.getSummary(), "")
                : description;

        boolean hasManualTags = !manualTagIds.isEmpty();
        metadata.tagIds = hasManualTags
                ? manualTagIds
                : mapAiTagNamesToIds(aiResult, allTags);

        // 只要进入 AI 分支，特征来源就是 ai，置信度尽量取 AI 分数。
        metadata.featureSource = "ai";
        metadata.confidenceByTagId = buildConfidenceMap(metadata.tagIds, aiResult, allTags, true);

        // 修复点：
        // 不再硬塞前两个标签（会造成“篮球内容被写成动漫/影视”）。
        // 先按标题+简介做关键词兜底，仍匹配不到则保持空标签，避免脏数据。
        if (metadata.tagIds.isEmpty() && allTags != null && !allTags.isEmpty()) {
            String fallbackText = firstNonBlank(title, "") + " " + firstNonBlank(metadata.description, "");
            metadata.tagIds = fallbackMatchTags(fallbackText, allTags, 5);
            if (!metadata.tagIds.isEmpty()) {
                metadata.confidenceByTagId = metadata.tagIds.stream()
                        .collect(Collectors.toMap(id -> id, id -> 0.45D, (a, b) -> a, LinkedHashMap::new));
            } else {
                metadata.confidenceByTagId = Collections.emptyMap();
                log.warn("AI标签为空且关键词兜底未命中: title={}, description={}", title, metadata.description);
            }
        }

        metadata.categoryId = manualCategoryId != null
                ? manualCategoryId
                : resolveCategoryId(aiResult, metadata.tagIds, allTags, allCategories, title, metadata.description);
        metadata.title = buildUploadTitle(title, aiResult == null ? null : aiResult.getGeneratedTitle(), metadata.description, metadata.tagIds, allTags);
        return metadata;
    }

    private List<String> resolveTagNames(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> idToName = tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> tag.getId() != null && tag.getName() != null)
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a, LinkedHashMap::new));
        List<String> out = new ArrayList<>();
        for (Long tagId : tagIds) {
            String name = idToName.get(tagId);
            if (name != null && !out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private Long resolveCategoryId(ContentAnalysisResult aiResult,
                                   List<Long> tagIds,
                                   List<Tag> allTags,
                                   List<Category> allCategories,
                                   String title,
                                   String description) {
        if (aiResult != null && aiResult.getSuggestedCategoryId() != null) {
            long aiId = aiResult.getSuggestedCategoryId().longValue();
            boolean exists = allCategories.stream().anyMatch(c -> Objects.equals(c.getId(), aiId));
            if (exists) {
                return aiId;
            }
        }

        Long mappedByTag = resolveCategoryIdByTags(tagIds, allTags, allCategories);
        if (mappedByTag != null) {
            return mappedByTag;
        }

        String text = (firstNonBlank(title, "") + " " + firstNonBlank(description, "")).toLowerCase();
        Long mappedByText = resolveCategoryIdByText(text, allCategories);
        if (mappedByText != null) {
            return mappedByText;
        }

        Long safeDefault = findCategoryIdByPreferredNames(allCategories, List.of("体育", "生活", "综合", "其他"));
        if (safeDefault != null) {
            return safeDefault;
        }
        return allCategories.get(0).getId();
    }

    private Long resolveCategoryIdByTags(List<Long> tagIds, List<Tag> allTags, List<Category> allCategories) {
        if (tagIds == null || tagIds.isEmpty() || allTags == null || allCategories == null || allCategories.isEmpty()) {
            return null;
        }
        Map<Long, String> tagIdToName = allTags.stream()
                .filter(tag -> tag.getId() != null && tag.getName() != null)
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        for (Long tagId : tagIds) {
            String name = tagIdToName.get(tagId);
            if (name == null) {
                continue;
            }
            Long categoryId = switch (name) {
                case "篮球", "足球", "NBA", "CBA", "欧冠", "英超", "世界杯", "赛事", "球星", "体育解说", "绝杀", "三分", "扣篮", "罚球" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("体育"));
                case "动画", "二次元", "鬼畜", "宅舞" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("动画"));
                case "电影", "电视剧", "纪录片", "影视", "影视解说", "混剪" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("影视"));
                case "音乐", "原声", "翻唱", "舞蹈", "说唱" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("音乐"));
                case "游戏", "电子竞技", "手游", "单机游戏", "主机游戏", "实况解说", "攻略" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("游戏"));
                case "科技", "数码", "编程", "前端", "后端", "Java", "SpringBoot", "Vue", "React", "MySQL", "Redis", "Docker", "数据库", "人工智能", "机器学习", "评测", "测评", "开箱" ->
                        findCategoryIdByPreferredNames(allCategories, List.of("科技", "数码", "知识"));
                default -> null;
            };
            if (categoryId != null) {
                return categoryId;
            }
        }
        return null;
    }

    private Long resolveCategoryIdByText(String text, List<Category> allCategories) {
        if (text == null || text.isBlank() || allCategories == null || allCategories.isEmpty()) {
            return null;
        }
        if (text.contains("nba") || text.contains("篮球") || text.contains("cba") || text.contains("欧冠") || text.contains("英超") || text.contains("世界杯") || text.contains("足球")) {
            return findCategoryIdByPreferredNames(allCategories, List.of("体育"));
        }
        if (text.contains("动画") || text.contains("动漫") || text.contains("二次元")) {
            return findCategoryIdByPreferredNames(allCategories, List.of("动画"));
        }
        if (text.contains("电影") || text.contains("电视剧") || text.contains("影视")) {
            return findCategoryIdByPreferredNames(allCategories, List.of("影视"));
        }
        if (text.contains("游戏") || text.contains("电竞") || text.contains("实况")) {
            return findCategoryIdByPreferredNames(allCategories, List.of("游戏"));
        }
        if (text.contains("音乐") || text.contains("翻唱") || text.contains("舞蹈") || text.contains("说唱")) {
            return findCategoryIdByPreferredNames(allCategories, List.of("音乐"));
        }
        return null;
    }

    private Long findCategoryIdByPreferredNames(List<Category> allCategories, List<String> preferredNames) {
        for (String preferredName : preferredNames) {
            for (Category category : allCategories) {
                if (category.getId() != null && preferredName.equals(category.getName())) {
                    return category.getId();
                }
            }
        }
        return null;
    }

    private List<Long> mapAiTagNamesToIds(ContentAnalysisResult aiResult, List<Tag> allTags) {
        if (aiResult == null || aiResult.getSuggestedTags() == null || aiResult.getSuggestedTags().isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> out = new ArrayList<>();
        for (String name : aiResult.getSuggestedTags()) {
            Long id = resolveTagIdByName(name, allTags);
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private Map<Long, Double> buildConfidenceMap(List<Long> tagIds,
                                                 ContentAnalysisResult aiResult,
                                                 List<Tag> allTags,
                                                 boolean useAiScores) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (!useAiScores) {
            return tagIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> 1.0D, (a, b) -> a, LinkedHashMap::new));
        }

        Map<Long, Double> fromAi = new LinkedHashMap<>();
        if (aiResult != null && aiResult.getTagScores() != null) {
            for (ScoredTag score : aiResult.getTagScores()) {
                if (score == null || score.getTag() == null) {
                    continue;
                }
                Long tagId = resolveTagIdByName(score.getTag(), allTags);
                if (tagId != null) {
                    double c = score.getConfidence() == null ? 0.6D : score.getConfidence();
                    fromAi.put(tagId, clamp01(c));
                }
            }
        }

        Map<Long, Double> out = new LinkedHashMap<>();
        for (Long tagId : tagIds) {
            out.put(tagId, fromAi.getOrDefault(tagId, 0.6D));
        }
        return out;
    }

    /**
     * 标签名映射（增强）：
     * 先精确匹配，再走标准化后的精确/包含匹配，降低模型返回词面差异导致的映射失败。
     */
    private Long resolveTagIdByName(String rawName, List<Tag> allTags) {
        if (rawName == null || rawName.isBlank() || allTags == null || allTags.isEmpty()) {
            return null;
        }
        String target = rawName.trim();
        for (Tag tag : allTags) {
            if (tag.getId() != null && target.equals(tag.getName())) {
                return tag.getId();
            }
        }
        String targetNorm = normalizeTagText(target);
        for (Tag tag : allTags) {
            if (tag.getId() == null || tag.getName() == null) {
                continue;
            }
            String candidateNorm = normalizeTagText(tag.getName());
            if (targetNorm.equals(candidateNorm)
                    || targetNorm.contains(candidateNorm)
                    || candidateNorm.contains(targetNorm)) {
                return tag.getId();
            }
        }
        return null;
    }

    /**
     * 关键词兜底匹配：
     * 仅在 AI 完全没给出可映射标签时使用，避免把无关标签写入特征表。
     */
    private List<Long> fallbackMatchTags(String text, List<Tag> allTags, int limit) {
        if (text == null || text.isBlank() || allTags == null || allTags.isEmpty()) {
            return Collections.emptyList();
        }
        String source = normalizeTagText(text);
        Map<Long, Integer> scoreByTagId = new LinkedHashMap<>();
        for (Tag tag : allTags) {
            if (tag.getId() == null || tag.getName() == null || tag.getName().isBlank()) {
                continue;
            }
            String tagNorm = normalizeTagText(tag.getName());
            if (tagNorm.length() < 2) {
                continue;
            }
            if (source.contains(tagNorm)) {
                scoreByTagId.put(tag.getId(), tagNorm.length());
            }
        }
        if (scoreByTagId.isEmpty()) {
            return Collections.emptyList();
        }
        return scoreByTagId.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    private String normalizeNullableText(String input) {
        return input == null ? "" : input.trim();
    }

    private String buildUploadTitle(String title,
                                    String generatedTitle,
                                    String description,
                                    List<Long> tagIds,
                                    List<Tag> allTags) {
        String normalizedTitle = normalizeNullableText(title);
        if (!normalizedTitle.isBlank()) {
            return truncate(normalizedTitle, 256);
        }
        String normalizedGeneratedTitle = normalizeNullableText(generatedTitle);
        if (isUsableGeneratedTitle(normalizedGeneratedTitle)) {
            return truncate(normalizedGeneratedTitle, 256);
        }
        String tagBasedTitle = buildTagBasedTitle(tagIds, allTags);
        if (!tagBasedTitle.isBlank()) {
            return truncate(tagBasedTitle, 256);
        }
        String fallback = normalizeNullableText(description);
        if (!fallback.isBlank()) {
            return truncate(fallback, 256);
        }
        return DEFAULT_VIDEO_TITLE;
    }

    private boolean isUsableGeneratedTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        if (title.length() < 4 || title.length() > 40) {
            return false;
        }
        String normalized = title.replaceAll("\\s+", "");
        return !normalized.contains("本视频")
                && !normalized.contains("围绕")
                && !normalized.contains("展开")
                && !normalized.contains("主题为")
                && !normalized.equals("精彩视频分享");
    }

    private String buildTagBasedTitle(List<Long> tagIds, List<Tag> allTags) {
        if (tagIds == null || tagIds.isEmpty() || allTags == null || allTags.isEmpty()) {
            return "";
        }
        Map<Long, String> tagIdToName = allTags.stream()
                .filter(tag -> tag.getId() != null && tag.getName() != null)
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        List<String> names = tagIds.stream()
                .map(tagIdToName::get)
                .filter(Objects::nonNull)
                .limit(2)
                .toList();
        if (names.isEmpty()) {
            return "";
        }
        if (names.contains("NBA") || names.contains("篮球")) {
            return "NBA篮球精彩片段";
        }
        if (names.contains("足球")) {
            return "足球赛事精彩片段";
        }
        return String.join("·", names) + "精彩分享";
    }

    private String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

    private String normalizeTagText(String input) {
        return input == null ? "" : input.trim().toLowerCase().replaceAll("\\s+", "");
    }

    private double clamp01(double v) {
        if (v < 0D) {
            return 0D;
        }
        if (v > 1D) {
            return 1D;
        }
        return v;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private void saveTags(Long videoId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueTagIds = tagIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long tagId : uniqueTagIds) {
            VideoTag relation = new VideoTag();
            relation.setVideoId(videoId);
            relation.setTagId(tagId);
            videoTagMapper.insert(relation);
        }
    }

    private void incrHotScore(Long videoId, double delta) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        redisTemplate.opsForZSet().incrementScore(key, videoId.toString(), delta);
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }

    private void validateVideoFile(MultipartFile videoFile) {
        String name = videoFile.getOriginalFilename();
        String ext = "";
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        Set<String> allowed = Set.of("mp4", "mov", "mkv", "webm", "avi", "flv", "m4v");
        if (!allowed.contains(ext)) {
            throw new BizException(400, "暂不支持该视频格式，支持: mp4/mov/mkv/webm/avi/flv/m4v");
        }
        String contentType = videoFile.getContentType();
        if (contentType != null && !contentType.startsWith("video/")) {
            throw new BizException(400, "上传文件不是视频类型");
        }
    }

    private static class UploadMetadata {
        private String title;
        private String description;
        private Long categoryId;
        private List<Long> tagIds = new ArrayList<>();
        private String featureSource;
        private Map<Long, Double> confidenceByTagId = Collections.emptyMap();
    }
}
