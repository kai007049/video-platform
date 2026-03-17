package com.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilibili.video.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 视频 Mapper
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    int incrementPlayCount(@Param("videoId") Long videoId, @Param("count") Long count);

    @Update("update video set save_count = save_count + #{count} where id = #{videoId}")
    int incrementSaveCount(@Param("videoId") Long videoId,@Param("count") Long count);

    @Update("update video set comment_count = comment_count + #{count} where id = #{videoId}")
    int incrementCommentCount(@Param("videoId") Long videoId, @Param("count") Long count);
}
