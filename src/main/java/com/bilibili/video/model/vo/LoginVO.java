package com.bilibili.video.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    @Schema(description = "JWT 访问令牌")
    private String token;
    @Schema(description = "用户信息")
    private UserInfoVO userInfo;
}
