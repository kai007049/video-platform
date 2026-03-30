package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.DanmuVO;
import com.bilibili.video.service.DanmuService;
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
