package com.bilibili.video.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频上传 DTO
 */
@Data
public class VideoUploadDTO {

    @Size(max = 256, message = "标题长度不能超过256个字符")
    private String title;

    @Size(max = 2000, message = "简介长度不能超过2000个字符")
    private String description;

    /**
     * 上传视频文件
     */
    private MultipartFile video;

    /**
     * 上传封面文件
     */
    private MultipartFile cover;

    /**
     * 可为空：为空时由后端规则自动补全分类
     */
    private Long categoryId;

    /**
     * 可为空：为空时由后端规则自动补全标签
     */
    @Size(max = 10, message = "标签数量不能超过10个")
    private List<Long> tagIds = new ArrayList<>();
}

