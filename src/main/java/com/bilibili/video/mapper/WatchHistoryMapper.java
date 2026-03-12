package com.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilibili.video.entity.WatchHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {
}
