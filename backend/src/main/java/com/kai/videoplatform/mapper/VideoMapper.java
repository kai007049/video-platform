package com.kai.videoplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kai.videoplatform.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 瑙嗛 Mapper
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    @Select("select * from video where id = #{videoId}")
    Video selectIncludingDeletedById(@Param("videoId") Long videoId);

    @Update("update video set deleted = 1, delete_time = now() where id = #{videoId} and deleted = 0")
    int softDeleteById(@Param("videoId") Long videoId);

    int incrementPlayCount(@Param("videoId") Long videoId, @Param("count") Long count);

    @Update("update video set save_count = save_count + #{count} where id = #{videoId} and deleted = 0")
    int incrementSaveCount(@Param("videoId") Long videoId, @Param("count") Long count);

    @Update("update video set comment_count = comment_count + #{count} where id = #{videoId} and deleted = 0")
    int incrementCommentCount(@Param("videoId") Long videoId, @Param("count") Long count);
}
