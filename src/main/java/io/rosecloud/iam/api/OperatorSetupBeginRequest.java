package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperatorSetupBeginRequest(
    @NotBlank String setupToken, @NotBlank @Size(min = 12) String password) {}