package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.MessageSummaryVO;
import com.bilibili.video.service.MessageCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message-center")
@RequiredArgsConstructor
@Tag(name = "消息中心")
public class MessageCenterController {

    private final MessageCenterService messageCenterService;

    @GetMapping("/summary")
    @Operation(summary = "消息中心汇总")
    public Result<MessageSummaryVO> summary(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(messageCenterService.summary(userId));
    }
}
