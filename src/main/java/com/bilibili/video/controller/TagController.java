package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.TagVO;
import com.bilibili.video.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
@Tag(name = "标签管理")
@Slf4j
public class TagController {

    private final TagService tagService;

    @Value("${agent.service.url:http://localhost:8001}")
    private String agentServiceUrl;

    @GetMapping("/list")
    @Operation(summary = "标签列表")
    public Result<List<TagVO>> list() {
        return Result.success(tagService.list());
    }

    @PostMapping("/recommend")
    @Operation(summary = "智能推荐标签")
    public Result<List<Long>> recommend(@RequestBody TagSuggestRequest request) {
        List<TagVO> allTags = tagService.list();
        if (allTags.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        try {
            List<String> candidateNames = allTags.stream().map(TagVO::getName).collect(Collectors.toList());
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", request.getTitle() == null ? "" : request.getTitle());
            payload.put("description", request.getDescription() == null ? "" : request.getDescription());
            payload.put("candidate_tags", candidateNames);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<?, ?> createRes = restTemplate.postForObject(
                    agentServiceUrl + "/tasks/tag",
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            if (createRes == null || createRes.get("task_id") == null) {
                return Result.success(new ArrayList<>());
            }

            String taskId = String.valueOf(createRes.get("task_id"));
            Map<?, ?> resultRes = restTemplate.getForObject(agentServiceUrl + "/tasks/" + taskId, Map.class);
            if (resultRes == null || !(resultRes.get("tags") instanceof List<?> tagNames)) {
                return Result.success(new ArrayList<>());
            }

            Map<String, Long> nameToId = new LinkedHashMap<>();
            for (TagVO tag : allTags) {
                nameToId.put(tag.getName(), tag.getId());
            }

            List<Long> tagIds = new ArrayList<>();
            for (Object t : tagNames) {
                if (t == null) continue;
                Long id = nameToId.get(String.valueOf(t));
                if (id != null && !tagIds.contains(id)) tagIds.add(id);
            }
            return Result.success(tagIds);
        } catch (Exception e) {
            log.warn("调用 agent-service 推荐标签失败", e);
            return Result.success(new ArrayList<>());
        }
    }

    @Data
    public static class TagSuggestRequest {
        private String title;
        private String description;
    }
}
