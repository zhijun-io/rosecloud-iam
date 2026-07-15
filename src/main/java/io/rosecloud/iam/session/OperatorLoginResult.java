package io.rosecloud.iam.session;

public record OperatorLoginResult(String accessToken, String refreshToken, long expiresInSeconds) {}
