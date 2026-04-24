package com.kai.videoplatform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.vo.NotificationVO;
import com.kai.videoplatform.service.NotificationService;
import com.kai.videoplatform.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Tag(name = "通知")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "通知列表")
    public Result<IPage<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(notificationService.listNotifications(userId, page, size));
    }

    @PostMapping("/read")
    @Operation(summary = "通知标记已读")
    public Result<Void> markRead(
            @RequestParam Long id,
            HttpServletRequest request) {
        Long userId = UserContext.get();
        notificationService.markRead(userId, id);
        return Result.success();
    }
}