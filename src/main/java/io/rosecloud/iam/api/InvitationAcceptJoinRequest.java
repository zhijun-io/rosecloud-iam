package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvitationAcceptJoinRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 12) String password,
    @Pattern(regexp = "\\d{6}") String totpCode) {}
