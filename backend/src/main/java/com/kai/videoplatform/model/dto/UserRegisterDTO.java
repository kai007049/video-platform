package com.kai.videoplatform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册 DTO
 */
@Data
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32)
    private String password;

}