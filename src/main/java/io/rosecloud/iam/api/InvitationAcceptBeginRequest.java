package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationAcceptBeginRequest(
    @NotBlank String token, @NotBlank @Size(min = 12) String password) {}
