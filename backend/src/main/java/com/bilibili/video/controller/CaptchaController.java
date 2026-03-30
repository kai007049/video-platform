package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.CaptchaVO;
import com.bilibili.video.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码控制器
 */
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
@Tag(name = "验证码接口")
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping
    @Operation(summary = "获取图形验证码")
    public Result<CaptchaVO> getCaptcha() {
        return Result.success(captchaService.createCaptcha());
    }
}
