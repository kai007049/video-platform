package com.kai.videoplatform.model.vo;

import lombok.Data;

/**
 * 点赞展示 VO
 */
@Data
public class LikeVo {
    private boolean liked;
    private Long likeCount;
}