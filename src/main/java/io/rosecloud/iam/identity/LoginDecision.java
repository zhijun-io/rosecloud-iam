package io.rosecloud.iam.identity;

import java.util.List;
import java.util.UUID;

public sealed interface LoginDecision {

  record SessionReady(UUID principalId) implements LoginDecision {}

  record ChallengeRequired(UUID challengeId, List<FactorBindingView> bindings)
      implements LoginDecision {}
}
