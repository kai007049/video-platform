package com.kai.videoplatform.seed;

public record SeedGenerationSummary(
        int authors,
        int users,
        int videos,
        int watches,
        int likes,
        int favorites,
        int follows,
        boolean searchReindexed
) {
    public String toLogLine() {
        return "authors=" + authors
                + ", users=" + users
                + ", videos=" + videos
                + ", watches=" + watches
                + ", likes=" + likes
                + ", favorites=" + favorites
                + ", follows=" + follows
                + ", searchReindexed=" + searchReindexed;
    }
}