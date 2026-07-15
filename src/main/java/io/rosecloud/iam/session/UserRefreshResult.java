package io.rosecloud.iam.session;

public record UserRefreshResult(String accessToken, String refreshToken, long expiresInSeconds) {}
