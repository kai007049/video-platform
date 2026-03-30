package com.bilibili.video.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论展示 VO（支持树形结构）
 */
@Data
@Schema(description = "评论展示 VO（支持树形结构）")
public class CommentVO {

    private Long id;
    private Long videoId;
    private Long userId;
    private String username;
    private String userAvatar;
    private String content;
    private Long parentId;
    private LocalDateTime createTime;
    private List<CommentVO> replies;
}
