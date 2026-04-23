package com.bilibili.video.seed;

import java.util.List;

public record InterestCluster(
        String key,
        String displayName,
        List<Long> categoryIds,
        List<Long> tagIds,
        List<String> titlePrefixes,
        List<String> titleKeywords
) {
}