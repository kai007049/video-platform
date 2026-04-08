package com.bilibili.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.SearchUserVO;
import com.bilibili.video.model.vo.VideoVO;

import java.util.List;
import com.bilibili.video.service.SearchService;
import com.bilibili.video.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "comprehensive") String sortBy) {
        Long userId = UserContext.get();
        searchService.recordSearchKeyword(userId, keyword);
        return Result.success(searchService.searchVideos(keyword, page, size, sortBy));
    }

    @GetMapping("/users")
    @Operation(summary = "按用户名模糊搜索用户")
    public Result<List<SearchUserVO>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        searchService.recordSearchKeyword(userId, keyword);
        return Result.success(searchService.searchUsers(keyword, page, size));
    }

    @GetMapping("/history")
    @Operation(summary = "获取搜索历史")
    public Result<List<String>> history(@RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.get();
        return Result.success(searchService.getSearchHistory(userId, limit));
    }

    @PostMapping("/history/clear")
    @Operation(summary = "清空搜索历史")
    public Result<Void> clearHistory() {
        Long userId = UserContext.get();
        searchService.clearSearchHistory(userId);
        return Result.success();
    }

    @GetMapping("/hot")
    @Operation(summary = "热门搜索")
    public Result<List<String>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(searchService.getHotSearches(limit));
    }

    @GetMapping("/hybrid")
    @Operation(summary = "混合搜索（当前为 ES 搜索实现）")
    public Result<IPage<VideoVO>> hybridSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        searchService.recordSearchKeyword(userId, keyword);
        return Result.success(searchService.hybridSearch(keyword, page, size, userId));
    }
}
