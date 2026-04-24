package com.kai.videoplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kai.videoplatform.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}