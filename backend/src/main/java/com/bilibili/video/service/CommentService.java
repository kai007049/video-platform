package com.bilibili.video.service;

import com.bilibili.video.model.dto.CommentDTO;
import com.bilibili.video.model.vo.CommentVO;

import java.util.List;

/**
 * 评论服务接口
 */
public interface CommentService {

    CommentVO add(CommentDTO dto, Long userId);

    List<CommentVO> listByVideoId(Long videoId);
}
