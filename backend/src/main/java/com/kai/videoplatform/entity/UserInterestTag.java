package com.kai.videoplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_interest_tag")
public class UserInterestTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long tagId;

    private Double weight;

    private LocalDateTime updatedAt;
}
