package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频点赞实体
 */
@Data
@TableName("video_like")
public class VideoLike {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private Long userId;
    private LocalDateTime createTime;
}
