package io.rosecloud.iam.api;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}
