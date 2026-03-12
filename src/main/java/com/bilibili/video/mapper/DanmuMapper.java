package com.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilibili.video.entity.Danmu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 弹幕 Mapper
 */
@Mapper
public interface DanmuMapper extends BaseMapper<Danmu> {
}
