package com.bilibili.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.dto.SendMessageDTO;
import com.bilibili.video.model.vo.MessageVO;
import com.bilibili.video.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Tag(name = "私信")
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @Operation(summary = "发送私信")
    public Result<Void> send(@RequestBody SendMessageDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageService.sendMessage(userId, dto);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "私信历史")
    public Result<IPage<MessageVO>> list(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(messageService.listMessages(userId, targetId, page, size));
    }

    @PostMapping("/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@RequestParam Long targetId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageService.markRead(userId, targetId);
        return Result.success();
    }
}
