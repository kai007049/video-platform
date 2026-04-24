package com.kai.videoplatform.seed;

public record SeedAuthorProfile(
        Long userId,
        String username,
        AuthorTier tier,
        String clusterKey
) {

    public enum AuthorTier {
        HEAD,
        MID,
        TAIL;

        public String value() {
            return name();
        }
    }

    public static SeedAuthorProfile of(Long userId, String username, AuthorTier tier, String clusterKey) {
        return new SeedAuthorProfile(userId, username, tier, clusterKey);
    }
}