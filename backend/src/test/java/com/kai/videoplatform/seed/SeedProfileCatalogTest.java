package com.kai.videoplatform.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedProfileCatalogTest {

    private final SeedProfileCatalog catalog = new SeedProfileCatalog();

    @Test
    void shouldResolveMediumDefaults() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.MEDIUM);

        SeedProfile profile = catalog.resolve(properties);

        assertThat(profile.authorCount()).isEqualTo(100);
        assertThat(profile.userCount()).isEqualTo(1000);
        assertThat(profile.videoCount()).isEqualTo(6000);
        assertThat(profile.watchCount()).isEqualTo(80000);
        assertThat(profile.likeCount()).isEqualTo(10000);
        assertThat(profile.favoriteCount()).isEqualTo(3500);
        assertThat(profile.followCount()).isEqualTo(1600);
        assertThat(profile.primaryPreferenceWeight()).isEqualTo(0.70D);
        assertThat(profile.secondaryPreferenceWeight()).isEqualTo(0.20D);
        assertThat(profile.explorationWeight()).isEqualTo(0.10D);
    }

    @Test
    void shouldApplyExplicitOverrides() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.SMALL);
        properties.setVideoCount(888);
        properties.setWatchCount(9999);

        SeedProfile profile = catalog.resolve(properties);

        assertThat(profile.videoCount()).isEqualTo(888);
        assertThat(profile.watchCount()).isEqualTo(9999);
        assertThat(profile.authorCount()).isEqualTo(20);
    }

    @Test
    void shouldRejectNegativeOverrideValues() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.SMALL);
        properties.setVideoCount(-1);

        assertThatThrownBy(() -> catalog.resolve(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("videoCount")
                .hasMessageContaining("must not be negative");
    }

    @Test
    void shouldFallbackToDefaultWhenOverrideIsZero() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.SMALL);
        properties.setVideoCount(0);

        SeedProfile profile = catalog.resolve(properties);

        assertThat(profile.videoCount()).isEqualTo(400);
    }

    @Test
    void shouldRejectInvalidSeedProfileValues() {
        assertThatThrownBy(() -> new SeedProfile(
                1, 1, 1, 1, 1, 1, 1,
                0.40D, 0.30D, 0.20D, 0.20D,
                0.70D, 0.20D, 0.10D, 30
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");

        assertThatThrownBy(() -> new SeedProfile(
                1, 1, 1, 1, 1, 1, 1,
                0.10D, 0.30D, 0.45D, 0.15D,
                0.70D, 0.20D, 0.10D, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recentDaysWindow");
    }

    @Test
    void shouldRejectEnabledSeedWithoutAppendMode() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(false);

        assertThatThrownBy(properties::validateOrThrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append=true");
    }
}