package com.bilibili.video.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论提交 DTO
 */
@Data
public class CommentDTO {

    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1024)
    private String content;

    private Long parentId;  // 回复时传父评论ID
}
