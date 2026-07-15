package io.rosecloud.iam.access;

import java.util.UUID;

/** Implemented by session; used when FactorBinding / RecoveryCode credentials change. */
public interface SessionRevocationPort {

  void revokeAllForPrincipal(String principalType, UUID principalId);
}
