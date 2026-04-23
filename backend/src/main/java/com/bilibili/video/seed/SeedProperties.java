package com.bilibili.video.seed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seed")
public class SeedProperties {

    private boolean enabled = false;
    private boolean append = false;
    private boolean searchReindex = false;
    private SeedMode mode = SeedMode.MEDIUM;
    private Long randomSeed = 42L;

    private Integer authorCount;
    private Integer userCount;
    private Integer videoCount;
    private Integer watchCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer followCount;

    public void validateOrThrow() {
        if (enabled && !append) {
            throw new IllegalStateException("Seed generation only supports append=true");
        }
    }
}