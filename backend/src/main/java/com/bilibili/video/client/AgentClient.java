package com.bilibili.video.client;

import com.bilibili.video.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent Service 统一客户端
 * 封装与 Python agent-service 的所有交互
 */
@Slf4j
@Component
public class AgentClient {

    @Value("${agent.service.url:http://localhost:8001}")
    private String agentServiceUrl;

    @Value("${backend.public-base-url:http://localhost:8080}")
    private String backendPublicBaseUrl;

    private final RestTemplate restTemplate;

    public AgentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 同步标签推荐（直接返回结果）
     */
    public TagRecommendResult recommendTags(String title, String description, List<String> candidateTags) {
        try {
            String url = agentServiceUrl + "/agent/tag/recommend";

            Map<String, Object> request = new HashMap<>();
            request.put("title", title);
            request.put("description", description);
            request.put("candidate_tags", candidateTags);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<TagRecommendResult> response = restTemplate.postForEntity(
                url, entity, TagRecommendResult.class
            );

            log.info("标签推荐成功: title={}, tags={}", title, response.getBody().getTags());
            return response.getBody();

        } catch (Exception e) {
            log.error("标签推荐失败: title={}", title, e);
            // 返回空结果，由调用方处理
            return TagRecommendResult.empty();
        }
    }

    /**
     * 语义搜索（向量检索）
     */
    public SemanticSearchResult semanticSearch(String query, int topK) {
        try {
            String url = agentServiceUrl + "/agent/search/semantic";

            Map<String, Object> request = new HashMap<>();
            request.put("query", query);
            request.put("top_k", topK);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SemanticSearchResult> response = restTemplate.postForEntity(
                url, entity, SemanticSearchResult.class
            );

            log.info("语义搜索成功: query={}, results={}", query, response.getBody().getVideoIds().size());
            return response.getBody();

        } catch (Exception e) {
            log.error("语义搜索失败: query={}", query, e);
            return SemanticSearchResult.empty();
        }
    }

    /**
     * 创建问答任务（异步）
     */
    public String createAskTask(String question, String token) {
        try {
            String url = agentServiceUrl + "/tasks/ask";

            Map<String, Object> request = new HashMap<>();
            request.put("question", question);
            request.put("page", 1);
            request.put("size", 10);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                headers.set("Authorization", token);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<TaskResponse> response = restTemplate.postForEntity(
                url, entity, TaskResponse.class
            );

            String taskId = response.getBody().getTaskId();
            log.info("问答任务创建成功: question={}, taskId={}", question, taskId);
            return taskId;

        } catch (Exception e) {
            log.error("问答任务创建失败: question={}", question, e);
            return null;
        }
    }

    /**
     * 查询任务结果
     */
    public TaskResult getTaskResult(String taskId) {
        try {
            String url = agentServiceUrl + "/tasks/" + taskId;
            ResponseEntity<TaskResult> response = restTemplate.getForEntity(url, TaskResult.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 获取视频推荐（基于用户兴趣）
     */
    public List<Long> getRecommendations(Long userId, String context, List<Long> excludeIds, int topK) {
        try {
            String url = agentServiceUrl + "/agent/recommend/videos";

            Map<String, Object> request = new HashMap<>();
            request.put("user_id", userId);
            request.put("context", context != null ? context : "");
            request.put("exclude_ids", excludeIds != null ? excludeIds : new ArrayList<>());
            request.put("top_k", topK);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            List<?> videoIds = (List<?>) response.getBody().get("video_ids");

            log.info("视频推荐成功: userId={}, results={}", userId, videoIds.size());
            return videoIds.stream()
                    .map(id -> Long.parseLong(String.valueOf(id)))
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.error("视频推荐失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 内容分析（自动标注）
     */
    public ContentAnalysisResult analyzeContent(String title, String description,
                                                String coverUrl,
                                                List<String> candidateTags,
                                                List<Map<String, Object>> candidateCategories) {
        try {
            String url = agentServiceUrl + "/agent/content/analyze";

            Map<String, Object> request = new HashMap<>();
            request.put("title", title);
            request.put("description", description != null ? description : "");
            request.put("cover_url", buildCoverProxyUrl(coverUrl));
            request.put("candidate_tags", candidateTags != null ? candidateTags : new ArrayList<>());
            request.put("candidate_categories", candidateCategories != null ? candidateCategories : new ArrayList<>());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ContentAnalysisResult> response = restTemplate.postForEntity(
                url, entity, ContentAnalysisResult.class
            );

            log.info("内容分析成功: title={}, coverUrlPresent={}", title, coverUrl != null && !coverUrl.isBlank());
            return response.getBody();

        } catch (Exception e) {
            log.error("内容分析失败: title={}", title, e);
            ContentAnalysisResult result = new ContentAnalysisResult();
            result.setSuggestedTags(new ArrayList<>());
            result.setGeneratedTitle("");
            return result;
        }
    }

    private String buildCoverProxyUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) {
            return coverUrl;
        }
        return UriComponentsBuilder.fromHttpUrl(backendPublicBaseUrl)
                .path("/file/cover")
                .queryParam("url", coverUrl)
                .build()
                .toUriString();
    }

    /**
     * 请求 agent-service 执行视频多模态语义索引。
     */
    public void indexVideoSemantic(Map<String, Object> payload) {
        try {
            String url = agentServiceUrl + "/agent/vector/index/video";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, entity, Map.class);
            log.info("视频语义索引请求成功: videoId={}", payload.get("video_id"));
        } catch (Exception e) {
            log.error("视频语义索引请求失败: videoId={}", payload.get("video_id"), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 请求 agent-service 删除视频向量索引。
     */
    public void deleteVideoSemantic(Long videoId) {
        try {
            String url = agentServiceUrl + "/agent/vector/delete/video";
            Map<String, Object> request = new HashMap<>();
            request.put("video_id", videoId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(url, entity, Map.class);
            log.info("视频向量删除请求成功: videoId={}", videoId);
        } catch (Exception e) {
            log.error("视频向量删除请求失败: videoId={}", videoId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 内容审核
     */
    public ModerationResult moderateContent(String content) {
        try {
            String url = agentServiceUrl + "/agent/content/moderate";

            Map<String, Object> request = new HashMap<>();
            request.put("content", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ModerationResult> response = restTemplate.postForEntity(
                url, entity, ModerationResult.class
            );

            log.info("内容审核完成: isRisky={}", response.getBody().getIsRisky());
            return response.getBody();

        } catch (Exception e) {
            log.error("内容审核失败", e);
            // 审核失败默认放行
            ModerationResult result = new ModerationResult();
            result.setIsRisky(false);
            result.setRiskLevel("safe");
            result.setReason("审核服务异常");
            return result;
        }
    }
}
