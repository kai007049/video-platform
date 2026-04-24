package com.kai.videoplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kai.videoplatform.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}