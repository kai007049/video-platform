package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.service.LikeService;
import com.bilibili.video.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞控制器
 */
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
@Tag(name = "点赞管理", description = "视频点赞、取消点赞、查询点赞状态接口")
public class LikeController {

    private final LikeService likeService;

    /**
     * 点赞视频（需登录）
     */
    @PostMapping("/{videoId}")
    @Operation(summary = "点赞视频", description = "对视频进行点赞，需要登录")
    public Result<Void> like(
            @Parameter(description = "视频ID", required = true) @PathVariable Long videoId,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        likeService.like(videoId, userId);
        return Result.success();
    }

    /**
     * 取消点赞（需登录）
     */
    @DeleteMapping("/{videoId}")
    @Operation(summary = "取消点赞", description = "取消对视频的点赞，需要登录")
    public Result<Void> unlike(
            @Parameter(description = "视频ID", required = true) @PathVariable Long videoId,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        likeService.unlike(videoId, userId);
        return Result.success();
    }

    /**
     * 查询是否已点赞（需登录）
     */
    @GetMapping("/{videoId}")
    @Operation(summary = "查询点赞状态", description = "查询当前用户是否已点赞该视频，需要登录")
    public Result<Map<String, Boolean>> isLiked(
            @Parameter(description = "视频ID", required = true) @PathVariable Long videoId,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        boolean liked = likeService.isLiked(videoId, userId);
        Map<String, Boolean> map = new HashMap<>();
        map.put("liked", liked);
        return Result.success(map);
    }
}
