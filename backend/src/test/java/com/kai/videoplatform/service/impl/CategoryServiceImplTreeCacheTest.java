package com.kai.videoplatform.service.impl;

import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.Category;
import com.kai.videoplatform.mapper.CategoryMapper;
import com.kai.videoplatform.model.vo.CategoryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTreeCacheTest {

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryMapper, redisTemplate, new ObjectMapper().findAndRegisterModules());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldIgnoreFlatTreeCacheAndRebuildRootsFromDatabase() {
        List<CategoryVO> flatCachedTree = List.of(
                categoryVO(5L, "科技", 0L),
                categoryVO(34L, "后端", 5L)
        );
        when(valueOperations.get(RedisConstants.CATEGORY_TREE_KEY)).thenReturn(flatCachedTree);
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(5L, "科技", 0L),
                category(33L, "前端", 5L),
                category(34L, "后端", 5L)
        ));

        List<CategoryVO> result = categoryService.tree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("科技");
        assertThat(result.get(0).getChildren())
                .extracting(CategoryVO::getName)
                .containsExactly("前端", "后端");
        verify(redisTemplate).delete(RedisConstants.CATEGORY_TREE_KEY);
        verify(valueOperations).set(eq(RedisConstants.CATEGORY_TREE_KEY), any(), eq(RedisConstants.METADATA_CACHE_TTL));
    }

    private Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(parentId);
        return category;
    }

    private CategoryVO categoryVO(Long id, String name, Long parentId) {
        CategoryVO vo = new CategoryVO();
        vo.setId(id);
        vo.setName(name);
        vo.setParentId(parentId);
        return vo;
    }
}