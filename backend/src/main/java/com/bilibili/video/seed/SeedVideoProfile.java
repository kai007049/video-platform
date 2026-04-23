package com.bilibili.video.seed;

import java.time.LocalDateTime;
import java.util.List;

public record SeedVideoProfile(
        Long videoId,
        Long authorId,
        String clusterKey,
        Long categoryId,
        List<Long> tagIds,
        boolean editorial,
        double potentialScore,
        LocalDateTime publishedAt
) {
}