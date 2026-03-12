package com.bilibili.video.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 第三方验证码校验 DTO
 */
@Data
public class CaptchaVerifyDTO {

    @NotBlank(message = "验证码Token不能为空")
    private String captchaToken;
}
