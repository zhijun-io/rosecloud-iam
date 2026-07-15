package io.rosecloud.iam.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FactorChallengeRequest(
    @NotNull UUID challengeId, String bindingId, String totpCode, String recoveryCode) {}
