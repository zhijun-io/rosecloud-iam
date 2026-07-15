package io.rosecloud.iam.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateMemberInvitationRequest(@NotBlank @Email String email, @NotBlank String roleCode) {}
