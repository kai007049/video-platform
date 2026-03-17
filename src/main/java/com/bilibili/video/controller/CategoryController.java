package com.bilibili.video.controller;

import com.bilibili.video.common.Result;
import com.bilibili.video.model.vo.CategoryVO;
import com.bilibili.video.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
@Tag(name = "分类管理")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    @Operation(summary = "分类树")
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @GetMapping("/list")
    @Operation(summary = "分类列表")
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.list());
    }
}
