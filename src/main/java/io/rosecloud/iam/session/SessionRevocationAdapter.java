package io.rosecloud.iam.session;

import io.rosecloud.iam.access.SessionRevocationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SessionRevocationAdapter implements SessionRevocationPort {

  private final LoginSessionRepository loginSessionRepository;
  private final Clock clock = Clock.systemUTC();

  SessionRevocationAdapter(LoginSessionRepository loginSessionRepository) {
    this.loginSessionRepository = loginSessionRepository;
  }

  @Override
  @Transactional
  public void revokeAllForPrincipal(String principalType, UUID principalId) {
    SessionPrincipalType type = SessionPrincipalType.valueOf(principalType);
    Instant now = Instant.now(clock);
    List<LoginSession> sessions =
        loginSessionRepository.findByPrincipalTypeAndPrincipalIdAndRevokedAtIsNullOrderByCreatedAtAsc(
            type, principalId);
    for (LoginSession session : sessions) {
      session.revoke(now);
    }
  }
}
