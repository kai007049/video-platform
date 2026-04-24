package com.kai.videoplatform.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图形验证码校验 DTO
 */
@Data
public class CaptchaLoginDTO {

    @NotBlank(message = "验证码Key不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaValue;
}