package com.kai.videoplatform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.exception.BizException;
import com.kai.videoplatform.model.dto.VideoUploadDTO;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.service.VideoService;
import com.kai.videoplatform.service.WatchHistoryService;
import com.kai.videoplatform.utils.MinioUtils;
import com.kai.videoplatform.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 视频控制器
 */
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Tag(name = "视频管理", description = "视频上传、列表、详情、播放等接口")
@Slf4j
public class VideoController {

    private final VideoService videoService;
    private final MinioUtils minioUtils;
    private final WatchHistoryService watchHistoryService;

    /**
     * 上传视频（标题/简介/分类/标签必填，封面可选）。
     */
    @PostMapping("/upload")
    @Operation(summary = "上传视频", description = "上传视频和封面，需要登录")
    public Result<VideoVO> upload(
            @Valid @ModelAttribute VideoUploadDTO dto) {
        Long userId = UserContext.get();
        return Result.success(videoService.upload(dto, userId));
    }

    @GetMapping("/list")
    @Operation(summary = "视频列表", description = "获取视频列表，支持分页")
    public Result<IPage<VideoVO>> list(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size,
            @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(videoService.list(page, size, userId));
    }

    @GetMapping("/creator")
    @Operation(summary = "创作者作品列表")
    public Result<IPage<VideoVO>> creatorVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        return Result.success(videoService.listCreatorVideos(userId, page, size));
    }

    @GetMapping("/liked")
    @Operation(summary = "点赞视频列表")
    public Result<IPage<VideoVO>> likedVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        return Result.success(videoService.listLikedVideos(userId, page, size));
    }

    @GetMapping("/favorite")
    @Operation(summary = "收藏视频列表")
    public Result<IPage<VideoVO>> favoriteVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        return Result.success(videoService.listFavoriteVideos(userId, page, size));
    }

    @GetMapping("/history")
    @Operation(summary = "观看历史列表")
    public Result<IPage<VideoVO>> historyVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        return Result.success(videoService.listHistoryVideos(userId, page, size));
    }

    @GetMapping("/recommended")
    @Operation(summary = "推荐流", description = "热门 + 最新 + 兴趣")
    public Result<IPage<VideoVO>> recommended(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String excludeVideoIds) {
        Long userId = UserContext.get();
        return Result.success(videoService.listRecommended(page, size, userId, parseExcludeIds(excludeVideoIds)));
    }

    @GetMapping("/hot")
    @Operation(summary = "热榜", description = "基于播放/点赞/评论/收藏的热榜")
    public Result<IPage<VideoVO>> hot(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = UserContext.get();
        return Result.success(videoService.listHot(page, size, userId));
    }

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

    @GetMapping("/{id}")
    @Operation(summary = "视频详情")
    public Result<VideoVO> getById(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id) {
        Long userId = UserContext.get();
        return Result.success(videoService.getById(id, userId));
    }

    @GetMapping("/{id}/stream")
    @Operation(summary = "视频流", description = "后端从 MinIO 转发视频流")
    public ResponseEntity<StreamingResponseBody> stream(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        var vo = videoService.getById(id, null);
        if (vo == null || vo.getVideoUrl() == null) {
            throw new BizException(404, "视频不存在");
        }

        try {
            long totalSize = minioUtils.getVideoSize(vo.getVideoUrl());
            String contentType = getVideoContentType(vo.getVideoUrl());

            if (rangeHeader == null || rangeHeader.isBlank()) {
                StreamingResponseBody body = outputStream -> {
                    try (InputStream in = minioUtils.getVideoStream(vo.getVideoUrl())) {
                        in.transferTo(outputStream);
                    } catch (Exception e) {
                        if (isClientDisconnected(e)) {
                            log.debug("client disconnected while streaming, videoId={}", id);
                            return;
                        }
                        throw new RuntimeException(e);
                    }
                };

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(totalSize))
                        .body(body);
            }

            HttpRange range = HttpRange.parseRanges(rangeHeader).get(0);
            long start = range.getRangeStart(totalSize);
            long end = range.getRangeEnd(totalSize);
            long contentLength = end - start + 1;

            StreamingResponseBody body = outputStream -> {
                try (InputStream in = minioUtils.getVideoStream(vo.getVideoUrl(), start, contentLength)) {
                    in.transferTo(outputStream);
                } catch (Exception e) {
                    if (isClientDisconnected(e)) {
                        log.debug("client disconnected while streaming range, videoId={}, range={}", id, rangeHeader);
                        return;
                    }
                    throw new RuntimeException(e);
                }
            };

            return ResponseEntity.status(206)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .body(body);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "视频流读取失败: " + e.getMessage());
        }
    }

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

    @PostMapping("/{id}/play")
    @Operation(summary = "记录播放量")
    public Result<Void> recordPlay(
            @Parameter(description = "视频ID", required = true) @PathVariable Long id) {
        videoService.recordPlayCount(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除视频")
    public Result<Void> deleteVideo(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        videoService.deleteVideo(id, userId);
        return Result.success();
    }

    private String getVideoContentType(String videoUrl) {
        if (videoUrl == null) return "video/mp4";
        String lower = videoUrl.toLowerCase();
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogg") || lower.endsWith(".ogv")) return "video/ogg";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        return "video/mp4";
    }

    private boolean isClientDisconnected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                String msg = current.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("broken pipe")
                            || lower.contains("connection reset by peer")
                            || lower.contains("connection reset")
                            || lower.contains("clientabortexception")
                            || lower.contains("你的主机中的软件中止了一个已建立的连接")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private Set<Long> parseExcludeIds(String excludeVideoIds) {
        if (excludeVideoIds == null || excludeVideoIds.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(excludeVideoIds.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .limit(200)
                .map(item -> {
                    try {
                        return Long.valueOf(item);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
