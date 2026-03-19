package com.bilibili.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.SearchUserVO;
import com.bilibili.video.model.vo.VideoVO;

import java.util.List;
import com.bilibili.video.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "搜索")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "搜索视频")
    public Result<IPage<VideoVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.success(searchService.searchVideos(keyword, page, size));
    }

    @GetMapping("/users")
    @Operation(summary = "按用户名模糊搜索用户")
    public Result<List<SearchUserVO>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.success(searchService.searchUsers(keyword, page, size));
    }
}
