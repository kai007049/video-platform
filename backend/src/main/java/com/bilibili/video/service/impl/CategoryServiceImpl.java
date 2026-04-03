package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Category;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.model.vo.CategoryVO;
import com.bilibili.video.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<CategoryVO> tree() {
        Object cached = redisTemplate.opsForValue().get(RedisConstants.CATEGORY_TREE_KEY);
        if (cached != null) {
            return objectMapper.convertValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryVO.class)
            );
        }

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
        redisTemplate.opsForValue().set(RedisConstants.CATEGORY_TREE_KEY, roots, RedisConstants.METADATA_CACHE_TTL);
        return roots;
    }

    @Override
    public List<CategoryVO> list() {
        Object cached = redisTemplate.opsForValue().get(RedisConstants.CATEGORY_LIST_KEY);
        if (cached != null) {
            return objectMapper.convertValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryVO.class)
            );
        }

        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getId));
        List<CategoryVO> list = new ArrayList<>();
        for (Category c : categories) {
            list.add(toVO(c));
        }
        redisTemplate.opsForValue().set(RedisConstants.CATEGORY_LIST_KEY, list, RedisConstants.METADATA_CACHE_TTL);
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
