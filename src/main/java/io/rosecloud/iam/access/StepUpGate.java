package io.rosecloud.iam.access;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** High-risk ops require recent LoginSession StepUp (password / challenge at login). */
@Component
public class StepUpGate {

  private final SessionStepUpPort sessionStepUpPort;

  public StepUpGate(SessionStepUpPort sessionStepUpPort) {
    this.sessionStepUpPort = sessionStepUpPort;
  }

  public void requireRecentStepUp(Authentication authentication) {
    PrincipalRef principal = resolve(authentication);
    sessionStepUpPort.requireRecentStepUp(
        principal.type(), principal.id(), principal.sessionId());
  }

  private PrincipalRef resolve(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthorizationException(HttpStatus.UNAUTHORIZED, "step-up required");
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof OperatorPrincipal operatorPrincipal) {
      return new PrincipalRef(
          "OPERATOR", operatorPrincipal.operatorId(), operatorPrincipal.sessionId());
    }
    if (principal instanceof UserPrincipal userPrincipal) {
      return new PrincipalRef("USER", userPrincipal.userId(), userPrincipal.sessionId());
    }
    if (principal instanceof TenantPrincipal tenantPrincipal) {
      return new PrincipalRef(
          "USER", tenantPrincipal.userId(), tenantPrincipal.sessionId());
    }
    throw new AuthorizationException(HttpStatus.UNAUTHORIZED, "step-up required");
  }

  private record PrincipalRef(String type, UUID id, UUID sessionId) {}
}
