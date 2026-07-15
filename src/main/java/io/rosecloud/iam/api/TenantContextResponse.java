package io.rosecloud.iam.api;

import java.util.List;

public record TenantContextResponse(
    String accessToken, String tokenType, long expiresIn, List<String> permissions) {}
