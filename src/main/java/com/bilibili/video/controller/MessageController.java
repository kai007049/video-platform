package com.bilibili.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.common.Result;
import com.bilibili.video.model.dto.SendMessageDTO;
import com.bilibili.video.model.vo.MessageVO;
import com.bilibili.video.service.MessageService;
import com.bilibili.video.utils.UserContext;
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
        Long userId = UserContext.get();
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
        Long userId = UserContext.get();
        return Result.success(messageService.listMessages(userId, targetId, page, size));
    }

    @PostMapping("/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@RequestParam Long targetId, HttpServletRequest request) {
        Long userId = UserContext.get();
        messageService.markRead(userId, targetId);
        return Result.success();
    }

    @GetMapping("/conversations")
    @Operation(summary = "会话列表")
    public Result<java.util.List<com.bilibili.video.model.vo.MessageConversationVO>> conversations(HttpServletRequest request) {
        Long userId = UserContext.get();
        return Result.success(messageService.listConversations(userId));
    }

    @PostMapping("/revoke")
    @Operation(summary = "撤回私信")
    public Result<Void> revoke(@RequestParam Long id, HttpServletRequest request) {
        Long userId = UserContext.get();
        messageService.revokeMessage(userId, id);
        return Result.success();
    }

    @PostMapping("/clear")
    @Operation(summary = "清空会话")
    public Result<Void> clear(@RequestParam Long targetId, HttpServletRequest request) {
        Long userId = UserContext.get();
        messageService.clearConversation(userId, targetId);
        return Result.success();
    }
}
