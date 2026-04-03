package com.bilibili.video.controller;

import com.bilibili.video.client.AgentClient;
import com.bilibili.video.client.dto.TagRecommendResult;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.TagVO;
import com.bilibili.video.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
    private final AgentClient agentClient;

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
            // 调用统一的 AgentClient
            List<String> candidateNames = allTags.stream()
                    .map(TagVO::getName)
                    .collect(Collectors.toList());

            TagRecommendResult result = agentClient.recommendTags(
                    request.getTitle() == null ? "" : request.getTitle(),
                    request.getDescription() == null ? "" : request.getDescription(),
                    candidateNames
            );

            if (result == null || result.getTags().isEmpty()) {
                return Result.success(new ArrayList<>());
            }

            // 将标签名映射为标签ID
            Map<String, Long> nameToId = new LinkedHashMap<>();
            for (TagVO tag : allTags) {
                nameToId.put(tag.getName(), tag.getId());
            }

            List<Long> tagIds = new ArrayList<>();
            for (String tagName : result.getTags()) {
                Long id = nameToId.get(tagName);
                if (id != null && !tagIds.contains(id)) {
                    tagIds.add(id);
                }
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
