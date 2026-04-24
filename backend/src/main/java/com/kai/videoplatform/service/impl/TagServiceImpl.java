package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.Tag;
import com.kai.videoplatform.mapper.TagMapper;
import com.kai.videoplatform.model.vo.TagVO;
import com.kai.videoplatform.service.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<TagVO> list() {
        Object cached = redisTemplate.opsForValue().get(RedisConstants.TAG_LIST_KEY);
        if (cached != null) {
            return objectMapper.convertValue(
                    cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TagVO.class)
            );
        }

        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getId));
        List<TagVO> list = new ArrayList<>();
        for (Tag tag : tags) {
            TagVO vo = new TagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            list.add(vo);
        }
        redisTemplate.opsForValue().set(RedisConstants.TAG_LIST_KEY, list, RedisConstants.METADATA_CACHE_TTL);
        return list;
    }
}