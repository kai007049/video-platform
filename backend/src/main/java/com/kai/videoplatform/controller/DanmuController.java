package com.kai.videoplatform.controller;

import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.vo.DanmuVO;
import com.kai.videoplatform.service.DanmuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/danmu")
@RequiredArgsConstructor
@Tag(name = "弹幕", description = "获取视频弹幕列表")
public class DanmuController {

    private final DanmuService danmuService;

    @GetMapping("/video/{videoId}")
    @Operation(summary = "获取视频弹幕列表")
    public Result<List<DanmuVO>> listByVideo(@PathVariable Long videoId) {
        return Result.success(danmuService.listByVideoId(videoId));
    }
}