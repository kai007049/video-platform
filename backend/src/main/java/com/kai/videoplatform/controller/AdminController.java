package com.kai.videoplatform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.common.Result;
import com.kai.videoplatform.model.vo.VideoVO;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.exception.BizException;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.service.SearchService;
import com.kai.videoplatform.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "管理后台", description = "需管理员权限")
public class AdminController {

    private final VideoService videoService;
    private final SearchService searchService;
    private final UserMapper userMapper;
    private final com.kai.videoplatform.utils.MinioUtils minioUtils;

    @GetMapping("/videos")
    @Operation(summary = "视频列表")
    public Result<IPage<VideoVO>> listVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        ensureAdmin(request);
        return Result.success(videoService.list(page, size, null));
    }

    @PutMapping("/video/{id}/recommend")
    @Operation(summary = "设置推荐")
    public Result<Void> setRecommend(@PathVariable Long id, @RequestParam boolean recommend, HttpServletRequest request) {
        ensureAdmin(request);
        videoService.setRecommended(id, recommend);
        return Result.success();
    }

    @GetMapping("/users")
    @Operation(summary = "用户列表")
    public Result<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        ensureAdmin(request);
        List<User> all = userMapper.selectList(null);
        int total = all.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<Map<String, Object>> list = all.subList(from, to).stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("avatar", u.getAvatar());
                    m.put("isAdmin", u.getIsAdmin());
                    m.put("createTime", u.getCreateTime());
                    return m;
                })
                .collect(Collectors.toList());
        Map<String, Object> res = new HashMap<>();
        res.put("records", list);
        res.put("total", total);
        res.put("current", page);
        res.put("pages", (total + size - 1) / size);
        return Result.success(res);
    }

    @PostMapping("/avatar/default")
    @Operation(summary = "上传默认头像")
    public Result<String> uploadDefaultAvatar(@RequestParam("avatar") org.springframework.web.multipart.MultipartFile avatar,
                                              HttpServletRequest request) {
        ensureAdmin(request);
        try {
            return Result.success(minioUtils.uploadDefaultAvatar(avatar));
        } catch (Exception e) {
            throw new BizException(500, "上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/search/reindex/videos")
    @Operation(summary = "全量重建视频搜索索引")
    public Result<Map<String, Object>> reindexVideos(HttpServletRequest request) {
        ensureAdmin(request);
        int count = searchService.reindexAllVideos();
        Map<String, Object> data = new HashMap<>();
        data.put("indexed", count);
        data.put("message", "视频搜索索引重建完成");
        return Result.success(data);
    }

    private void ensureAdmin(HttpServletRequest request) {
        Long userId = com.kai.videoplatform.utils.UserContext.get();
        if (userId == null) throw new BizException(401, "请先登录");
        User user = userMapper.selectById(userId);
        if (user == null || user.getIsAdmin() == null || !user.getIsAdmin()) {
            throw new BizException(403, "需要管理员权限");
        }
    }
}