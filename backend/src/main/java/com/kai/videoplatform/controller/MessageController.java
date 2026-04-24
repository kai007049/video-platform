package com.kai.videoplatform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.dto.SendMessageDTO;
import com.kai.videoplatform.model.vo.MessageVO;
import com.kai.videoplatform.service.MessageService;
import com.kai.videoplatform.utils.MinioUtils;
import com.kai.videoplatform.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Tag(name = "私信")
public class MessageController {

    private final MessageService messageService;
    private final MinioUtils minioUtils;

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
    public Result<java.util.List<com.kai.videoplatform.model.vo.MessageConversationVO>> conversations(HttpServletRequest request) {
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

    @PostMapping("/upload-image")
    @Operation(summary = "上传私信图片")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = UserContext.get();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "图片不能为空");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return Result.error(400, "仅支持图片上传");
        }
        try {
            String objectName = minioUtils.uploadMessageImage(file);
            return Result.success(objectName);
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }
}