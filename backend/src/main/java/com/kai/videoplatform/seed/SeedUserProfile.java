package com.kai.videoplatform.seed;

public record SeedUserProfile(
        Long userId,
        String username,
        SeedUserPersona persona,
        String primaryClusterKey,
        String secondaryClusterKey,
        double followBias,
        double explorationBias
) {
}