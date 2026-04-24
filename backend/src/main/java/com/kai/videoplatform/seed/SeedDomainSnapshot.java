package com.kai.videoplatform.seed;

import java.util.List;
import java.util.Map;

public record SeedDomainSnapshot(
        List<InterestCluster> clusters,
        Map<String, InterestCluster> clusterByKey,
        List<Long> allCategoryIds,
        List<Long> allTagIds
) {
    public InterestCluster requireCluster(String key) {
        InterestCluster cluster = clusterByKey.get(key);
        if (cluster == null) {
            throw new IllegalArgumentException("Unknown cluster: " + key);
        }
        return cluster;
    }
}