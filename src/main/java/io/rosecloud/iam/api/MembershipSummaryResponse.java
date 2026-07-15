package io.rosecloud.iam.api;

import java.util.UUID;

public record MembershipSummaryResponse(
    UUID membershipId, UUID tenantId, String tenantName, String roleCode) {}
