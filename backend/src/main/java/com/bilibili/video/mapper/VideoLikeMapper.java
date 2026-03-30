package com.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilibili.video.entity.VideoLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 视频点赞 Mapper
 */
@Mapper
public interface VideoLikeMapper extends BaseMapper<VideoLike> {

    int incrementLikeCount(@Param("videoId") Long videoId, @Param("delta") int delta);
}
