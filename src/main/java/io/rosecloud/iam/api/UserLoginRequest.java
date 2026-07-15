package io.rosecloud.iam.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserLoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    @Pattern(regexp = "\\d{6}") String totpCode) {}
