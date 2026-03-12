package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 弹幕实体
 */
@Data
@TableName("danmu")
@Schema(description = "弹幕")
public class Danmu {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "视频ID")
    private Long videoId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "弹幕内容")
    private String content;

    @Schema(description = "弹幕时间点")
    private Integer timePoint;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
