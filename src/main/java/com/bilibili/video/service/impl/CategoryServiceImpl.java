package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.Category;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.model.vo.CategoryVO;
import com.bilibili.video.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> tree() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getId));
        Map<Long, CategoryVO> map = new HashMap<>();
        List<CategoryVO> roots = new ArrayList<>();
        for (Category c : categories) {
            CategoryVO vo = toVO(c);
            map.put(c.getId(), vo);
        }
        for (Category c : categories) {
            CategoryVO current = map.get(c.getId());
            Long parentId = c.getParentId();
            if (parentId == null || parentId == 0) {
                roots.add(current);
            } else {
                CategoryVO parent = map.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(current);
                } else {
                    roots.add(current);
                }
            }
        }
        return roots;
    }

    @Override
    public List<CategoryVO> list() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getId));
        List<CategoryVO> list = new ArrayList<>();
        for (Category c : categories) {
            list.add(toVO(c));
        }
        return list;
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        return vo;
    }
}
