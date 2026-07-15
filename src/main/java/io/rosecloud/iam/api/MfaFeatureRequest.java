package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MfaFeatureRequest(@NotNull Boolean enabled, @NotBlank String reason) {}
