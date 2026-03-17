package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频实体
 */
@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "作者ID")
    private Long authorId;

    @Schema(description = "封面URL")
    private String coverUrl;

    @Schema(description = "预览URL")
    private String previewUrl;

    @Schema(description = "视频URL")
    private String videoUrl;

    @Schema(description = "播放次数")
    private Long playCount;

    @Schema(description = "点赞次数")
    private Long likeCount;

    @Schema(description = "收藏次数")
    private Long saveCount;

    @Schema(description = "视频时长（秒）")
    private Integer durationSeconds;

    @Schema(description = "是否推荐")
    private Boolean isRecommended;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
