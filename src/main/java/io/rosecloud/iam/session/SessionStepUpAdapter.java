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
  public void requireRecentStepUp(String principalType, UUID principalId, UUID sessionId) {
    Instant now = Instant.now(clock);
    Instant earliest = now.minus(properties.stepUpWindow());

    if (sessionId == null) {
      throw new AuthorizationException(
          HttpStatus.UNAUTHORIZED, "step-up required: session claim missing");
    }

    LoginSession session =
        loginSessionRepository
            .findById(sessionId)
            .orElseThrow(
                () ->
                    new AuthorizationException(
                        HttpStatus.UNAUTHORIZED, "step-up required: unknown session"));

    if (session.principalType() != SessionPrincipalType.valueOf(principalType)
        || !session.principalId().equals(principalId)
        || session.isRevoked()
        || session.isExpired(now)) {
      throw new AuthorizationException(
          HttpStatus.UNAUTHORIZED, "step-up required: session not usable");
    }

    Instant satisfiedAt = session.stepUpSatisfiedAt();
    if (satisfiedAt == null || satisfiedAt.isBefore(earliest)) {
      throw new AuthorizationException(
          HttpStatus.UNAUTHORIZED, "step-up required: StepUp window expired");
    }
  }
}
