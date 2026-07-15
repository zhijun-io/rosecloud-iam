package io.rosecloud.iam.access;

import java.util.List;
import java.util.UUID;

public record TenantPrincipal(
    UUID userId, UUID tenantId, UUID membershipId, List<String> permissions) {}
