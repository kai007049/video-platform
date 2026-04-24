package com.kai.videoplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kai.videoplatform.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}