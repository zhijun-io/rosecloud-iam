package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;

public record MfaResetRequest(@NotBlank String reason) {}
