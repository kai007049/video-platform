package com.bilibili.video.service;

import com.bilibili.video.model.vo.TagVO;

import java.util.List;

public interface TagService {

    /**
     * 标签列表（扁平）
     */
    List<TagVO> list();
}
