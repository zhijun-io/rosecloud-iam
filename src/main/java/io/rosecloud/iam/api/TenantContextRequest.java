package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TenantContextRequest(@NotNull UUID membershipId) {}
