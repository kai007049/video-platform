package com.kai.videoplatform.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图形验证码响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO {

    @Schema(description = "验证码Key")
    private String captchaKey;

    @Schema(description = "验证码图片Base64")
    private String imageBase64;
}