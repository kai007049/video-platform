package com.kai.videoplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kai.videoplatform.entity.VideoTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频标签关联 Mapper
 */
@Mapper
public interface VideoTagMapper extends BaseMapper<VideoTag> {
}