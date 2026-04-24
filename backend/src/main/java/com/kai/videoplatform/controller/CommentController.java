package com.kai.videoplatform.controller;

import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.dto.CommentDTO;
import com.kai.videoplatform.model.vo.CommentVO;
import com.kai.videoplatform.service.CommentService;
import com.kai.videoplatform.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "评论发表、查看接口")
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表评论（需登录）
     */
    @PostMapping
    @Operation(summary = "发表评论", description = "发表评论，需要登录")
    public Result<CommentVO> add(
            @Valid @RequestBody CommentDTO dto,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(commentService.add(dto, userId));
    }

    /**
     * 查看视频评论列表
     */
    @GetMapping("/video/{videoId}")
    @Operation(summary = "查看视频评论", description = "获取视频的评论列表")
    public Result<List<CommentVO>> listByVideoId(
            @Parameter(description = "视频ID", required = true) @PathVariable Long videoId) {
        return Result.success(commentService.listByVideoId(videoId));
    }
}