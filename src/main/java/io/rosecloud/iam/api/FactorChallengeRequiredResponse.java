package io.rosecloud.iam.api;

import io.rosecloud.iam.identity.FactorBindingView;
import java.util.List;
import java.util.UUID;

public record FactorChallengeRequiredResponse(
    String status, UUID challengeId, List<FactorBindingView> bindings) {

  public static FactorChallengeRequiredResponse of(
      UUID challengeId, List<FactorBindingView> bindings) {
    return new FactorChallengeRequiredResponse("FACTOR_CHALLENGE_REQUIRED", challengeId, bindings);
  }
}
