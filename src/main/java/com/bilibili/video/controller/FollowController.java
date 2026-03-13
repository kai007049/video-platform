package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.service.FollowService;
import com.bilibili.video.model.vo.FollowUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
@Tag(name = "关注", description = "关注/取关 UP主")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}")
    @Operation(summary = "关注")
    public Result<Void> follow(@PathVariable Long userId, HttpServletRequest request) {
        Long followerId = (Long) request.getAttribute("userId");
        followService.follow(followerId, userId);
        return Result.success();
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "取关")
    public Result<Void> unfollow(@PathVariable Long userId, HttpServletRequest request) {
        Long followerId = (Long) request.getAttribute("userId");
        followService.unfollow(followerId, userId);
        return Result.success();
    }

    @GetMapping("/check/{userId}")
    @Operation(summary = "是否已关注")
    public Result<Boolean> isFollowing(@PathVariable Long userId, HttpServletRequest request) {
        Long followerId = getUserIdNullable(request);
        return Result.success(followerId != null && followService.isFollowing(followerId, userId));
    }

    @GetMapping("/count/{userId}")
    @Operation(summary = "关注数/粉丝数")
    public Result<FollowCountVO> count(@PathVariable Long userId) {
        long following = followService.getFollowingCount(userId);
        long fans = followService.getFanCount(userId);
        return Result.success(new FollowCountVO(following, fans));
    }

    @GetMapping("/following/{userId}")
    @Operation(summary = "关注列表")
    public Result<java.util.List<FollowUserVO>> following(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = getUserIdNullable(request);
        return Result.success(followService.getFollowingList(userId, currentUserId));
    }

    @GetMapping("/fans/{userId}")
    @Operation(summary = "粉丝列表")
    public Result<java.util.List<FollowUserVO>> fans(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = getUserIdNullable(request);
        return Result.success(followService.getFanList(userId, currentUserId));
    }

    private Long getUserIdNullable(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        return attr != null ? (Long) attr : null;
    }

    public record FollowCountVO(long followingCount, long fanCount) {}
}
