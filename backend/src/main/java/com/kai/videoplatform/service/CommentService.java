package com.kai.videoplatform.service;

import com.kai.videoplatform.model.dto.CommentDTO;
import com.kai.videoplatform.model.vo.CommentVO;

import java.util.List;

/**
 * 评论服务接口
 */
public interface CommentService {

    CommentVO add(CommentDTO dto, Long userId);

    List<CommentVO> listByVideoId(Long videoId);
}