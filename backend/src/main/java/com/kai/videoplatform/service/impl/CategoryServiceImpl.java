package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.Category;
import com.kai.videoplatform.mapper.CategoryMapper;
import com.kai.videoplatform.model.vo.CategoryVO;
import com.kai.videoplatform.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            List<CategoryVO> cachedTree = objectMapper.convertValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryVO.class)
            );
            if (isValidCategoryTree(cachedTree)) {
                return cachedTree;
            }
            redisTemplate.delete(RedisConstants.CATEGORY_TREE_KEY);
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

    private boolean isValidCategoryTree(List<CategoryVO> tree) {
        if (tree == null || tree.isEmpty()) {
            return true;
        }
        Set<Long> seenIds = new HashSet<>();
        for (CategoryVO root : tree) {
            if (root == null || root.getId() == null) {
                return false;
            }
            Long parentId = root.getParentId();
            if (parentId != null && parentId != 0) {
                return false;
            }
            if (!seenIds.add(root.getId())) {
                return false;
            }
            if (!isValidCategoryChildren(root.getChildren(), root.getId(), seenIds)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidCategoryChildren(List<CategoryVO> children, Long expectedParentId, Set<Long> seenIds) {
        if (children == null || children.isEmpty()) {
            return true;
        }
        for (CategoryVO child : children) {
            if (child == null || child.getId() == null) {
                return false;
            }
            if (child.getParentId() == null || !child.getParentId().equals(expectedParentId)) {
                return false;
            }
            if (!seenIds.add(child.getId())) {
                return false;
            }
            if (!isValidCategoryChildren(child.getChildren(), child.getId(), seenIds)) {
                return false;
            }
        }
        return true;
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        return vo;
    }
}