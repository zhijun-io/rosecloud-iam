package io.rosecloud.iam.access;

import java.util.UUID;

/** Implemented by session; access stays free of session entity types. */
public interface SessionStepUpPort {

  void requireRecentStepUp(String principalType, UUID principalId, UUID sessionId);
}
