package com.kai.videoplatform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "标题不能为空")
    @Size(max = 256, message = "标题长度不能超过256个字符")
    private String title;

    @NotBlank(message = "简介不能为空")
    @Size(max = 2000, message = "简介长度不能超过2000个字符")
    private String description;

    /**
     * 上传视频文件
     */
    @NotNull(message = "视频文件不能为空")
    private MultipartFile video;

    /**
     * 上传封面文件
     */
    private MultipartFile cover;

    /**
     * 视频分类，必填
     */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /**
     * 视频标签，至少选择一个
     */
    @NotEmpty(message = "请至少选择一个标签")
    @Size(max = 10, message = "标签数量不能超过10个")
    private List<Long> tagIds = new ArrayList<>();
}
