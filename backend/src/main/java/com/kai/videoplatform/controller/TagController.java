package com.kai.videoplatform.controller;

import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.vo.TagVO;
import com.kai.videoplatform.service.TagService;
import com.kai.videoplatform.service.impl.LocalContentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
@Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;
    private final LocalContentAnalysisService localContentAnalysisService;

    @GetMapping("/list")
    @Operation(summary = "标签列表")
    public Result<List<TagVO>> list() {
        return Result.success(tagService.list());
    }

    @PostMapping("/recommend")
    @Operation(summary = "推荐标签")
    public Result<List<Long>> recommend(@RequestBody TagSuggestRequest request) {
        List<TagVO> allTags = tagService.list();
        if (allTags.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<Long> tagIds = localContentAnalysisService.recommendTagIds(
                request.getTitle() == null ? "" : request.getTitle(),
                request.getDescription() == null ? "" : request.getDescription(),
                allTags
        );
        return Result.success(tagIds);
    }

    @Data
    public static class TagSuggestRequest {
        private String title;
        private String description;
    }
}