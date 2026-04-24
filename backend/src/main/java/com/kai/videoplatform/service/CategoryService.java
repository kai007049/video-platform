package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    /**
     * 获取分类树
     */
    List<CategoryVO> tree();

    /**
     * 获取分类列表（扁平）
     */
    List<CategoryVO> list();
}