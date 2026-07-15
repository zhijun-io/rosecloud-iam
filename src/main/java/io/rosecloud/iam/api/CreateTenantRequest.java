package io.rosecloud.iam.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(@NotBlank String name, @NotBlank @Email String ownerEmail) {}
