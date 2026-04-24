package com.kai.videoplatform.seed;

import org.springframework.stereotype.Component;

@Component
public class SeedProfileCatalog {

    private static final UserBehaviorProfile SMALL_USER_BEHAVIOR = new UserBehaviorProfile(
            0.10D, 0.30D, 0.45D, 0.15D,
            0.70D, 0.20D, 0.10D
    );
    private static final UserBehaviorProfile DEFAULT_USER_BEHAVIOR = new UserBehaviorProfile(
            0.12D, 0.33D, 0.40D, 0.15D,
            0.70D, 0.20D, 0.10D
    );

    private static final SeedProfile SMALL_DEFAULT = profileOf(
            20, 120, 400, 2000, 250, 100, 50,
            SMALL_USER_BEHAVIOR,
            30
    );
    private static final SeedProfile MEDIUM_DEFAULT = profileOf(
            100, 1000, 6000, 80000, 10000, 3500, 1600,
            DEFAULT_USER_BEHAVIOR,
            90
    );
    private static final SeedProfile LARGE_DEFAULT = profileOf(
            300, 4000, 25000, 400000, 60000, 20000, 9000,
            DEFAULT_USER_BEHAVIOR,
            120
    );

    public SeedProfile resolve(SeedProperties properties) {
        SeedProfile defaults = defaultsFor(properties.getMode());

        return new SeedProfile(
                pick(properties.getAuthorCount(), defaults.authorCount(), "authorCount"),
                pick(properties.getUserCount(), defaults.userCount(), "userCount"),
                pick(properties.getVideoCount(), defaults.videoCount(), "videoCount"),
                pick(properties.getWatchCount(), defaults.watchCount(), "watchCount"),
                pick(properties.getLikeCount(), defaults.likeCount(), "likeCount"),
                pick(properties.getFavoriteCount(), defaults.favoriteCount(), "favoriteCount"),
                pick(properties.getFollowCount(), defaults.followCount(), "followCount"),
                defaults.heavyUserRatio(),
                defaults.mediumUserRatio(),
                defaults.lightUserRatio(),
                defaults.coldUserRatio(),
                defaults.primaryPreferenceWeight(),
                defaults.secondaryPreferenceWeight(),
                defaults.explorationWeight(),
                defaults.recentDaysWindow()
        );
    }

    private SeedProfile defaultsFor(SeedMode mode) {
        if (mode == SeedMode.SMALL) {
            return SMALL_DEFAULT;
        }
        if (mode == SeedMode.LARGE) {
            return LARGE_DEFAULT;
        }
        return MEDIUM_DEFAULT;
    }

    private static SeedProfile profileOf(
            int authorCount,
            int userCount,
            int videoCount,
            int watchCount,
            int likeCount,
            int favoriteCount,
            int followCount,
            UserBehaviorProfile userBehavior,
            int recentDaysWindow
    ) {
        return new SeedProfile(
                authorCount,
                userCount,
                videoCount,
                watchCount,
                likeCount,
                favoriteCount,
                followCount,
                userBehavior.heavyUserRatio(),
                userBehavior.mediumUserRatio(),
                userBehavior.lightUserRatio(),
                userBehavior.coldUserRatio(),
                userBehavior.primaryPreferenceWeight(),
                userBehavior.secondaryPreferenceWeight(),
                userBehavior.explorationWeight(),
                recentDaysWindow
        );
    }

    private int pick(Integer overrideValue, int defaultValue, String fieldName) {
        if (overrideValue == null || overrideValue == 0) {
            return defaultValue;
        }
        if (overrideValue < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return overrideValue;
    }

    private record UserBehaviorProfile(
            double heavyUserRatio,
            double mediumUserRatio,
            double lightUserRatio,
            double coldUserRatio,
            double primaryPreferenceWeight,
            double secondaryPreferenceWeight,
            double explorationWeight
    ) {
    }
}