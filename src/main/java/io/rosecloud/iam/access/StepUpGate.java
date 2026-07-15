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

  public void requireRecentReauth(Authentication authentication) {
    PrincipalRef principal = resolve(authentication);
    sessionStepUpPort.requireRecentReauth(principal.type(), principal.id());
  }

  /** @deprecated Prefer {@link #requireRecentReauth(Authentication)} */
  public void requireRecentPasswordAndTotp(Authentication authentication) {
    requireRecentReauth(authentication);
  }

  private PrincipalRef resolve(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthorizationException(HttpStatus.UNAUTHORIZED, "step-up required");
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof OperatorPrincipal operatorPrincipal) {
      return new PrincipalRef("OPERATOR", operatorPrincipal.operatorId());
    }
    if (principal instanceof UserPrincipal userPrincipal) {
      return new PrincipalRef("USER", userPrincipal.userId());
    }
    if (principal instanceof TenantPrincipal tenantPrincipal) {
      return new PrincipalRef("USER", tenantPrincipal.userId());
    }
    throw new AuthorizationException(HttpStatus.UNAUTHORIZED, "step-up required");
  }

  private record PrincipalRef(String type, UUID id) {}
}
