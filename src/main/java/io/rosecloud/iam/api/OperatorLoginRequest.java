package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OperatorLoginRequest(
    @NotBlank String password, @Pattern(regexp = "\\d{6}") String totpCode) {}
