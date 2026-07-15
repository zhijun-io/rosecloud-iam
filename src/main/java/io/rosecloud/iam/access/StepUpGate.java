package io.rosecloud.iam.access;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Interface-level StepUp hook for high-risk operations (Plan I6). Thin slice has no issued StepUp
 * token yet; call sites retain the seam until Operator MFA reset / owner transfer ships.
 */
@Component
public class StepUpGate {

  public void requireRecentPasswordAndTotp(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthorizationException(
          HttpStatus.UNAUTHORIZED, "step-up required: recent password + TOTP");
    }
    // No StepUp claim issuance in this slice — presence of an authenticated principal is enough to
    // retain the call-site hook without changing thin-slice endpoint semantics.
  }
}
