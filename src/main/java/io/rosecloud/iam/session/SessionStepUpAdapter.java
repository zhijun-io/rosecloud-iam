package io.rosecloud.iam.session;

import io.rosecloud.iam.access.AuthorizationException;
import io.rosecloud.iam.access.SessionStepUpPort;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class SessionStepUpAdapter implements SessionStepUpPort {

  private final LoginSessionRepository loginSessionRepository;
  private final RosecloudIamProperties properties;
  private final Clock clock = Clock.systemUTC();

  SessionStepUpAdapter(
      LoginSessionRepository loginSessionRepository, RosecloudIamProperties properties) {
    this.loginSessionRepository = loginSessionRepository;
    this.properties = properties;
  }

  @Override
  public void requireRecentReauth(String principalType, UUID principalId) {
    SessionPrincipalType type = SessionPrincipalType.valueOf(principalType);
    Instant now = Instant.now(clock);
    Instant earliest = now.minus(properties.stepUpWindow());

    LoginSession session =
        loginSessionRepository
            .findFirstByPrincipalTypeAndPrincipalIdAndRevokedAtIsNullOrderByStepUpSatisfiedAtDesc(
                type, principalId)
            .orElseThrow(
                () ->
                    new AuthorizationException(
                        HttpStatus.UNAUTHORIZED, "step-up required: no active session"));

    Instant satisfiedAt = session.stepUpSatisfiedAt();
    if (satisfiedAt == null || satisfiedAt.isBefore(earliest)) {
      throw new AuthorizationException(
          HttpStatus.UNAUTHORIZED, "step-up required: recent reauthentication");
    }
  }
}
