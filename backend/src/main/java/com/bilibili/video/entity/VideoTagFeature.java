package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_tag_feature")
public class VideoTagFeature {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    private Long tagId;

    private Double confidence;

    private String source;

    private String version;

    private LocalDateTime updatedAt;
}

