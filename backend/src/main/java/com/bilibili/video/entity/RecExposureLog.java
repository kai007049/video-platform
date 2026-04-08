package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rec_exposure_log")
public class RecExposureLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long videoId;

    private String reqId;

    private String scene;

    private Integer rank;

    /** 推荐分数，便于后续分析排序效果。 */
    private Double score;

    /** 候选来自哪些召回通道，例如 hot,tag,category。 */
    private String channels;

    /** 推荐策略版本，便于线上效果回溯。 */
    private String strategyVersion;

    private LocalDateTime ts;
}

