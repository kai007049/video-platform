package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.dto.AccountLoginDTO;
import com.bilibili.video.model.dto.UserRegisterDTO;
import com.bilibili.video.model.vo.CreatorStatsVO;
import com.bilibili.video.model.vo.LoginVO;
import com.bilibili.video.model.vo.UpProfileVO;
import com.bilibili.video.model.vo.UserInfoVO;
import com.bilibili.video.service.UserService;
import com.bilibili.video.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户接口")
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    /**
     * 用户名/手机号/邮箱登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户名/手机号/邮箱登录")
    public Result<LoginVO> login(@Valid @RequestBody AccountLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 获取用户信息（需登录）
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户信息")
    public Result<UserInfoVO> info() {
        Long userId = UserContext.get();
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * UP主主页（公开）
     */
    @GetMapping("/profile/{id}")
    @Operation(summary = "UP主主页")
    public Result<UpProfileVO> profile(@PathVariable Long id) {
        Long currentUserId = UserContext.get();
        return Result.success(userService.getUpProfile(id, currentUserId));
    }

    /**
     * 创作者数据面板（需登录）
     */
    @GetMapping("/creator/stats")
    @Operation(summary = "创作者数据")
    public Result<CreatorStatsVO> creatorStats() {
        Long userId = UserContext.get();
        return Result.success(userService.getCreatorStats(userId));
    }

    @PostMapping("/avatar")
    @Operation(summary = "更新头像")
    public Result<String> updateAvatar(@RequestParam("avatar") MultipartFile avatar) {
        Long userId = UserContext.get();
        return Result.success(userService.updateAvatar(userId, avatar));
    }
}
