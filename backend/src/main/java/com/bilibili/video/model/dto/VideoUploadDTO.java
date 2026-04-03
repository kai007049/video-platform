package com.bilibili.video.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 视频上传 DTO
 */
@Data
public class VideoUploadDTO {

    @Size(max = 256)
    private String title;

    @Size(max = 2000)
    private String description;

    /**
     * 可为空：为空时由 AI 自动补全分类
     */
    private Long categoryId;

    /**
     * 可为空：为空时由 AI 自动补全标签
     */
    private List<Long> tagIds;
}

