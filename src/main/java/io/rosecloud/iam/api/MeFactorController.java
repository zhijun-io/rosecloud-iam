package io.rosecloud.iam.api;

import io.rosecloud.iam.access.StepUpGate;
import io.rosecloud.iam.access.TenantPrincipal;
import io.rosecloud.iam.access.UserPrincipal;
import io.rosecloud.iam.identity.FactorEnrollmentService;
import io.rosecloud.iam.identity.TotpService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/factors")
class MeFactorController {

  private final FactorEnrollmentService factorEnrollmentService;
  private final StepUpGate stepUpGate;

  MeFactorController(FactorEnrollmentService factorEnrollmentService, StepUpGate stepUpGate) {
    this.factorEnrollmentService = factorEnrollmentService;
    this.stepUpGate = stepUpGate;
  }

  @PostMapping("/totp/begin")
  FactorEnrollmentBeginResponse beginTotp(Authentication authentication) {
    stepUpGate.requireRecentReauth(authentication);
    TotpService.TotpEnrollment enrollment =
        factorEnrollmentService.beginTotp(requireUserId(authentication));
    return new FactorEnrollmentBeginResponse(enrollment.secret(), enrollment.otpauthUrl());
  }

  @PostMapping("/totp/complete")
  RecoveryCodesResponse completeTotp(
      Authentication authentication, @Valid @RequestBody FactorEnrollmentCompleteRequest request) {
    stepUpGate.requireRecentReauth(authentication);
    List<String> codes =
        factorEnrollmentService.completeTotp(requireUserId(authentication), request.totpCode());
    return new RecoveryCodesResponse(codes);
  }

  @DeleteMapping("/totp")
  ResponseEntity<Void> revokeTotp(Authentication authentication) {
    stepUpGate.requireRecentReauth(authentication);
    factorEnrollmentService.revokeTotp(requireUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/recovery-codes")
  RecoveryCodesResponse regenerateRecoveryCodes(Authentication authentication) {
    stepUpGate.requireRecentReauth(authentication);
    return new RecoveryCodesResponse(
        factorEnrollmentService.regenerateRecoveryCodes(requireUserId(authentication)));
  }

  private UUID requireUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserPrincipal userPrincipal) {
      return userPrincipal.userId();
    }
    if (principal instanceof TenantPrincipal tenantPrincipal) {
      return tenantPrincipal.userId();
    }
    throw new io.rosecloud.iam.access.AuthorizationException(
        HttpStatus.UNAUTHORIZED, "user authentication required");
  }
}
