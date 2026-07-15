package io.rosecloud.iam.access;

import java.util.UUID;

public record UserPrincipal(UUID userId, UUID sessionId) {}
