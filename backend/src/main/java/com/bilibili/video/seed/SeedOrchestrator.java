package com.bilibili.video.seed;

import com.bilibili.video.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SeedOrchestrator {

    private final SeedProperties properties;
    private final SeedProfileCatalog profileCatalog;
    private final SeedDomainCatalog domainCatalog;
    private final SeedUserGenerator userGenerator;
    private final SeedVideoGenerator videoGenerator;
    private final SeedBehaviorGenerator behaviorGenerator;
    private final SeedProjectionService projectionService;
    private final SearchService searchService;

    public SeedGenerationSummary run() {
        properties.validateOrThrow();
        SeedProfile profile = profileCatalog.resolve(properties);
        SeedDomainSnapshot snapshot = domainCatalog.load();
        Random random = new Random(properties.getRandomSeed() == null ? 42L : properties.getRandomSeed());
        SeedPopulation population = userGenerator.generate(profile, snapshot, random);
        List<SeedVideoProfile> videos = videoGenerator.generate(profile, snapshot, population, random);
        SeedBehaviorResult result = behaviorGenerator.generate(profile, snapshot, population, videos, random);
        projectionService.persist(result);

        boolean searchReindexed = false;
        if (properties.isSearchReindex()) {
            searchService.reindexAllVideos();
            searchReindexed = true;
        }

        return new SeedGenerationSummary(
                population.authors().size(),
                population.users().size(),
                videos.size(),
                result.watches().size(),
                result.likes().size(),
                result.favorites().size(),
                result.follows().size(),
                searchReindexed
        );
    }
}