package com.bilibili.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.service.VideoService;
import com.bilibili.video.service.WatchHistoryService;
import com.bilibili.video.utils.MinioUtils;
import com.bilibili.video.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * 视频控制器
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Tag(name = "视频管理", description = "视频上传、列表、详情、播放记录接口")
@Slf4j
public class VideoController {

    private final VideoService videoService;
    private final MinioUtils minioUtils;
    private final WatchHistoryService watchHistoryService;

    /**
     * 上传视频（需登录）
     * multipart/form-data: video(必填), cover(可选), title(必填), description(可选)
     */
    @PostMapping("/upload")
    @Operation(summary = "上传视频", description = "上传视频和封面，需要登录")
    public Result<VideoVO> upload(
            @Parameter(description = "视频文件", required = true) @RequestParam("video") MultipartFile videoFile,
            @Parameter(description = "封面文件") @RequestParam(value = "cover", required = false) MultipartFile coverFile,
            @Parameter(description = "视频标题", required = true) @RequestParam("title") String title,
            @Parameter(description = "视频描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "分类ID", required = true) @RequestParam("categoryId") Long categoryId,
            @Parameter(description = "标签ID列表", required = true) @RequestParam("tagIds") java.util.List<Long> tagIds,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCategoryId(categoryId);
        dto.setTagIds(tagIds);
        return Result.success(videoService.upload(videoFile, coverFile, dto, userId));
    }

    /**
     * 视频列表（分页）
     */
    @GetMapping("/list")
    @Operation(summary = "视频列表", description = "获取视频列表，支持分页")
    public Result<IPage<VideoVO>> list(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.list(page, size, userId));
    }

    /**
     * 创作者作品列表（需登录）
     */
    @GetMapping("/creator")
    @Operation(summary = "创作者作品列表")
    public Result<IPage<VideoVO>> creatorVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listCreatorVideos(userId, page, size));
    }

    /**
     * 点赞视频列表（需登录）
     */
    @GetMapping("/liked")
    @Operation(summary = "点赞视频列表")
    public Result<IPage<VideoVO>> likedVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listLikedVideos(userId, page, size));
    }

    /**
     * 收藏视频列表（需登录）
     */
    @GetMapping("/favorite")
    @Operation(summary = "收藏视频列表")
    public Result<IPage<VideoVO>> favoriteVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listFavoriteVideos(userId, page, size));
    }

    /**
     * 观看历史列表（需登录）
     */
    @GetMapping("/history")
    @Operation(summary = "观看历史列表")
    public Result<IPage<VideoVO>> historyVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listHistoryVideos(userId, page, size));
    }

    /**
     * 推荐流（热门+最新混合）
     */
    @GetMapping("/recommended")
    @Operation(summary = "推荐流", description = "热门+最新混合排序的视频列表")
    public Result<IPage<VideoVO>> recommended(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listRecommended(page, size, userId));
    }

    @GetMapping("/hot")
    @Operation(summary = "热榜", description = "基于播放/点赞/评论/收藏的热榜")
    public Result<IPage<VideoVO>> hot(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listHot(page, size, userId));
    }

    /**
     * UP主视频列表
     */
    @GetMapping("/author/{authorId}")
    @Operation(summary = "UP主视频列表")
    public Result<IPage<VideoVO>> byAuthor(
            @PathVariable Long authorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.listByAuthor(authorId, page, size, userId));
    }

    /**
     * 视频详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "视频详情", description = "获取视频详细信息")
    public Result<VideoVO> getById(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.getById(id, userId));
    }

    /**
     * 视频流（用于播放，后端从 MinIO 转发，解决私有桶无法直接访问问题）
     */
    @GetMapping("/{id}/stream")
    @Operation(summary = "视频流", description = "获取视频播放流，支持 HTML5 video 播放")
    public ResponseEntity<StreamingResponseBody> stream(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id) {
        var vo = videoService.getById(id, null);
        if (vo == null || vo.getVideoUrl() == null) {
            throw new BizException(404, "视频不存在");
        }
        StreamingResponseBody body = outputStream -> {
            try (InputStream in = minioUtils.getVideoStream(vo.getVideoUrl())) {
                in.transferTo(outputStream);
            } catch (Exception e) {
                log.info("视频流异常");
                throw new RuntimeException(e);
            }
        };
        String contentType = getVideoContentType(vo.getVideoUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Accept-Ranges", "bytes")
                .body(body);
    }

    /**
     * 保存观看进度（继续播放用，需登录）
     */
    @PostMapping("/{id}/progress")
    @Operation(summary = "保存观看进度")
    public Result<Void> saveProgress(
            @PathVariable Long id,
            @RequestParam int seconds,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        if (userId != null) {
            watchHistoryService.saveProgress(userId, id, seconds);
        }
        return Result.success();
    }

    /**
     * 记录播放量（播放时调用，需登录）
     */
    @PostMapping("/{id}/play")
    @Operation(summary = "记录播放量", description = "记录视频播放量")
    public Result<Void> recordPlay(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id) {
        videoService.recordPlayCount(id);
        return Result.success();
    }

    /**
     * 删除视频（需登录，仅作者）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除视频")
    public Result<Void> deleteVideo(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        videoService.deleteVideo(id, userId);
        return Result.success();
    }

    /**
     * 获取视频内容类型
     * @param videoUrl
     * @return
     */
    private String getVideoContentType(String videoUrl) {
        if (videoUrl == null) return "video/mp4";
        String lower = videoUrl.toLowerCase();
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogg") || lower.endsWith(".ogv")) return "video/ogg";
        return "video/mp4";
    }

}
