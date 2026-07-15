package io.rosecloud.iam.session;

public record UserLoginResult(String accessToken, String refreshToken, long expiresInSeconds) {}
