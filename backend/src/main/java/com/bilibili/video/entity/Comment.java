package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体
 */
@Data
@TableName("comment")
@Schema(description = "评论")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "视频ID")
    private Long videoId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "父评论ID")
    private Long parentId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "点赞数")
    private Long likeCount;
}
