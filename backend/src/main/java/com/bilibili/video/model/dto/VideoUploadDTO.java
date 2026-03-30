package com.bilibili.video.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 视频上传 DTO
 */
@Data
public class VideoUploadDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 256)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotEmpty(message = "标签不能为空")
    private List<Long> tagIds;
}
