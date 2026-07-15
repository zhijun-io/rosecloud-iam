package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;

public record StepUpPasswordRequest(@NotBlank String password) {}
