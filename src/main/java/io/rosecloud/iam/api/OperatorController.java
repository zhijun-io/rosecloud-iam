package io.rosecloud.iam.api;

import io.rosecloud.iam.access.OperatorPrincipal;
import io.rosecloud.iam.access.StepUpGate;
import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.FactorBindingService;
import io.rosecloud.iam.identity.FactorChallengeService;
import io.rosecloud.iam.identity.LoginDecision;
import io.rosecloud.iam.identity.MfaFeatureService;
import io.rosecloud.iam.identity.TotpService;
import io.rosecloud.iam.operator.OperatorFactorService;
import io.rosecloud.iam.operator.OperatorSetupService;
import io.rosecloud.iam.session.OperatorLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
class OperatorController {

  private final OperatorSetupService operatorSetupService;
  private final RefreshCookieFactory refreshCookieFactory;
  private final FactorChallengeService factorChallengeService;
  private final MfaFeatureService mfaFeatureService;
  private final OperatorFactorService operatorFactorService;
  private final FactorBindingService factorBindingService;
  private final StepUpGate stepUpGate;
  private final AuditService auditService;

  OperatorController(
      OperatorSetupService operatorSetupService,
      RefreshCookieFactory refreshCookieFactory,
      FactorChallengeService factorChallengeService,
      MfaFeatureService mfaFeatureService,
      OperatorFactorService operatorFactorService,
      FactorBindingService factorBindingService,
      StepUpGate stepUpGate,
      AuditService auditService) {
    this.operatorSetupService = operatorSetupService;
    this.refreshCookieFactory = refreshCookieFactory;
    this.factorChallengeService = factorChallengeService;
    this.mfaFeatureService = mfaFeatureService;
    this.operatorFactorService = operatorFactorService;
    this.factorBindingService = factorBindingService;
    this.stepUpGate = stepUpGate;
    this.auditService = auditService;
  }

  @PostMapping("/setup/begin")
  ResponseEntity<OperatorSetupBeginResponse> beginSetup(
      @Valid @RequestBody OperatorSetupBeginRequest request) {
    OperatorSetupService.SetupBeginResult result =
        operatorSetupService.beginSetup(request.setupToken(), request.password());
    return ResponseEntity.ok(new OperatorSetupBeginResponse(result.totpSecret(), result.otpauthUrl()));
  }

  @PostMapping("/setup/complete")
  ResponseEntity<Void> completeSetup(@Valid @RequestBody OperatorSetupCompleteRequest request) {
    operatorSetupService.completeSetup(request.setupToken(), request.totpCode());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  ResponseEntity<?> login(@Valid @RequestBody OperatorLoginRequest request) {
    LoginDecision decision = operatorSetupService.login(request.password());
    if (decision instanceof LoginDecision.ChallengeRequired challenge) {
      return ResponseEntity.ok(
          FactorChallengeRequiredResponse.of(challenge.challengeId(), challenge.bindings()));
    }
    LoginDecision.SessionReady ready = (LoginDecision.SessionReady) decision;
    OperatorLoginResult result = operatorSetupService.issueSession(ready.principalId());
    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken()).toString())
        .body(new OperatorLoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }

  @PostMapping("/factor-challenge")
  ResponseEntity<OperatorLoginResponse> completeFactorChallenge(
      @Valid @RequestBody FactorChallengeRequest request, HttpServletRequest httpRequest) {
    UUID operatorId =
        factorChallengeService.verify(
            request.challengeId(),
            request.bindingId(),
            request.totpCode(),
            request.recoveryCode(),
            httpRequest.getRemoteAddr());
    auditService.append(AuditService.OPERATOR_LOGIN_SUCCEEDED, operatorId, "login succeeded");
    OperatorLoginResult result = operatorSetupService.issueSession(operatorId);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken()).toString())
        .body(new OperatorLoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }

  @GetMapping("/mfa-feature")
  Map<String, Boolean> getMfaFeature() {
    return Map.of("enabled", mfaFeatureService.isEnabled());
  }

  @PutMapping("/mfa-feature")
  ResponseEntity<Map<String, Boolean>> setMfaFeature(
      @Valid @RequestBody MfaFeatureRequest request, Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    boolean from = mfaFeatureService.isEnabled();
    boolean to = request.enabled();
    mfaFeatureService.setEnabled(to);
    auditService.append(
        "mfa.feature_changed",
        operatorId(authentication),
        "mfaFeature "
            + from
            + " -> "
            + to
            + "; reason="
            + request.reason()
            + "; stepUp=satisfied");
    return ResponseEntity.ok(Map.of("enabled", to));
  }

  @PostMapping("/factors/totp/begin")
  FactorBindingBeginResponse beginTotp(Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    TotpService.TotpEnrollment enrollment =
        operatorFactorService.beginTotp(operatorId(authentication));
    return new FactorBindingBeginResponse(enrollment.secret(), enrollment.otpauthUrl());
  }

  @PostMapping("/factors/totp/complete")
  RecoveryCodesResponse completeTotp(
      Authentication authentication, @Valid @RequestBody FactorBindingCompleteRequest request) {
    stepUpGate.requireRecentStepUp(authentication);
    List<String> codes =
        operatorFactorService.completeTotp(operatorId(authentication), request.totpCode());
    return new RecoveryCodesResponse(codes);
  }

  @DeleteMapping("/factors/totp")
  ResponseEntity<Void> revokeTotp(Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    operatorFactorService.revokeTotp(operatorId(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/factors/recovery-codes")
  RecoveryCodesResponse regenerateRecoveryCodes(Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    return new RecoveryCodesResponse(
        operatorFactorService.regenerateRecoveryCodes(operatorId(authentication)));
  }

  @PostMapping("/users/{userId}/mfa-reset")
  ResponseEntity<Void> resetUserMfa(
      @PathVariable UUID userId,
      @Valid @RequestBody MfaResetRequest request,
      Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    factorBindingService.operatorReset(userId, request.reason());
    auditService.append(
        "factor.operator_reset_requested",
        operatorId(authentication),
        "reset user " + userId + "; reason=" + request.reason() + "; stepUp=satisfied");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/operators/{operatorId}/mfa-reset")
  ResponseEntity<Void> resetOperatorMfa(
      @PathVariable UUID operatorId,
      @Valid @RequestBody MfaResetRequest request,
      Authentication authentication) {
    stepUpGate.requireRecentStepUp(authentication);
    operatorFactorService.resetCredentials(operatorId, request.reason());
    auditService.append(
        "factor.operator_reset_requested",
        operatorId(authentication),
        "reset operator " + operatorId + "; reason=" + request.reason() + "; stepUp=satisfied");
    return ResponseEntity.noContent().build();
  }

  private UUID operatorId(Authentication authentication) {
    return ((OperatorPrincipal) authentication.getPrincipal()).operatorId();
  }
}
