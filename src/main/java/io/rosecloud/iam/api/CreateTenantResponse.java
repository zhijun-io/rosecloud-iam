package io.rosecloud.iam.api;

import java.util.UUID;

public record CreateTenantResponse(UUID tenantId, UUID invitationId, String status) {}
