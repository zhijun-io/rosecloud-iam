package io.rosecloud.iam.access;

import java.util.UUID;

/** Implemented by session; access stays free of session entity types. */
public interface SessionStepUpPort {

  void requireRecentReauth(String principalType, UUID principalId);
}
