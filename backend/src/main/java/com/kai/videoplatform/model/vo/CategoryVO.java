package com.kai.videoplatform.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {
    private Long id;
    private String name;
    private Long parentId;
    private List<CategoryVO> children = new ArrayList<>();
}