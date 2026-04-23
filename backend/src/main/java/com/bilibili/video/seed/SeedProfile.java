package com.bilibili.video.seed;

public record SeedProfile(
        int authorCount,
        int userCount,
        int videoCount,
        int watchCount,
        int likeCount,
        int favoriteCount,
        int followCount,
        double heavyUserRatio,
        double mediumUserRatio,
        double lightUserRatio,
        double coldUserRatio,
        double primaryPreferenceWeight,
        double secondaryPreferenceWeight,
        double explorationWeight,
        int recentDaysWindow
) {

    private static final double RATIO_TOLERANCE = 1.0E-9D;

    public SeedProfile {
        requirePositive(authorCount, "authorCount");
        requirePositive(userCount, "userCount");
        requirePositive(videoCount, "videoCount");
        requirePositive(watchCount, "watchCount");
        requirePositive(likeCount, "likeCount");
        requirePositive(favoriteCount, "favoriteCount");
        requirePositive(followCount, "followCount");
        requireRatio(heavyUserRatio, "heavyUserRatio");
        requireRatio(mediumUserRatio, "mediumUserRatio");
        requireRatio(lightUserRatio, "lightUserRatio");
        requireRatio(coldUserRatio, "coldUserRatio");
        requireRatio(primaryPreferenceWeight, "primaryPreferenceWeight");
        requireRatio(secondaryPreferenceWeight, "secondaryPreferenceWeight");
        requireRatio(explorationWeight, "explorationWeight");
        requirePositive(recentDaysWindow, "recentDaysWindow");
        requireRatioSum(
                heavyUserRatio + mediumUserRatio + lightUserRatio + coldUserRatio,
                "user ratios"
        );
        requireRatioSum(
                primaryPreferenceWeight + secondaryPreferenceWeight + explorationWeight,
                "preference weights"
        );
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
    }

    private static void requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void requireRatioSum(double sum, String label) {
        if (Math.abs(sum - 1.0D) > RATIO_TOLERANCE) {
            throw new IllegalArgumentException(label + " must sum to 1.0");
        }
    }
}