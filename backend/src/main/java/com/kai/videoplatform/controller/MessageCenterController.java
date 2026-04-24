package com.kai.videoplatform.controller;

import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.vo.MessageSummaryVO;
import com.kai.videoplatform.service.MessageCenterService;
import com.kai.videoplatform.utils.UserContext;
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
        Long userId = UserContext.get();
        return Result.success(messageCenterService.summary(userId));
    }
}