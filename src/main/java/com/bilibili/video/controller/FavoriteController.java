package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
@Tag(name = "收藏", description = "收藏/取消收藏视频")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{videoId}")
    @Operation(summary = "收藏")
    public Result<Void> add(@PathVariable Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.add(userId, videoId);
        return Result.success();
    }

    @DeleteMapping("/{videoId}")
    @Operation(summary = "取消收藏")
    public Result<Void> remove(@PathVariable Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteService.remove(userId, videoId);
        return Result.success();
    }
}
