package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.TagVO;

import java.util.List;

public interface TagService {

    /**
     * 标签列表（扁平）
     */
    List<TagVO> list();
}