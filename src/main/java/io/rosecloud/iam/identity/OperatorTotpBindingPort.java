package io.rosecloud.iam.identity;

import java.util.Optional;
import java.util.UUID;

/** Cross-module port so identity FactorChallenge can verify Operator TOTP without depending on operator entities. */
public interface OperatorTotpBindingPort {

  boolean hasBinding(UUID operatorId);

  Optional<String> ciphertextHint(UUID operatorId);

  boolean verifyTotp(UUID operatorId, String totpCode);
}
