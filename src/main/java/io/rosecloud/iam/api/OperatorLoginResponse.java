package io.rosecloud.iam.api;

public record OperatorLoginResponse(String accessToken, String tokenType, long expiresIn) {}
