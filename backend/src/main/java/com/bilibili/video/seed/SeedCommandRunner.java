package com.bilibili.video.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedCommandRunner implements ApplicationRunner {

    private final SeedProperties properties;
    private final SeedOrchestrator orchestrator;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        SeedGenerationSummary summary = orchestrator.run();
        log.warn("Seed generation complete: {}", summary.toLogLine());
        context.close();
    }
}