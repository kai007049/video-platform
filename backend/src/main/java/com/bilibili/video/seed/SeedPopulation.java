package com.bilibili.video.seed;

import com.bilibili.video.entity.User;

import java.util.List;

public record SeedPopulation(
        List<SeedAuthorProfile> authors,
        List<SeedUserProfile> users,
        List<User> rawUsers
) {
}
