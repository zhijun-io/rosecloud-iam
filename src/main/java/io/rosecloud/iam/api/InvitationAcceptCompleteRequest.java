package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvitationAcceptCompleteRequest(
    @NotBlank String token, @Pattern(regexp = "\\d{6}") String totpCode) {}
