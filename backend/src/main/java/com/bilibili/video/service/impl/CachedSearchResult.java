package com.bilibili.video.service.impl;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class CachedSearchResult {

    private List<Long> ids = Collections.emptyList();
    private Long total = 0L;
    /** Unix 时间戳（秒） */
    private Long generatedAt;

    public CachedSearchResult() {
    }

    public CachedSearchResult(List<Long> ids, Long total, Long generatedAt) {
        this.ids = ids;
        this.total = total;
        this.generatedAt = generatedAt;
    }
}
