package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.User;

import java.util.List;

public record SeedPopulation(
        List<SeedAuthorProfile> authors,
        List<SeedUserProfile> users,
        List<User> rawUsers
) {
}