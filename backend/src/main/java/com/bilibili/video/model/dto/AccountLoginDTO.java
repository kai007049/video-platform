package com.bilibili.video.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名/手机号/邮箱登录 DTO
 */
@Data
public class AccountLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "验证码Key不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaValue;
}
