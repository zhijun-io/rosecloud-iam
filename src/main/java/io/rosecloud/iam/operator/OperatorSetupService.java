package io.rosecloud.iam.operator;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.FactorChallengeService;
import io.rosecloud.iam.identity.LoginDecision;
import io.rosecloud.iam.identity.MfaFeatureService;
import io.rosecloud.iam.identity.SessionPrincipalKind;
import io.rosecloud.iam.session.OperatorLoginResult;
import io.rosecloud.iam.session.OperatorSessionService;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorSetupService {

  private final PlatformOperatorRepository platformOperatorRepository;
  private final OperatorSetupTokenRepository operatorSetupTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final Sha256Hasher sha256Hasher;
  private final AuditService auditService;
  private final OperatorSessionService operatorSessionService;
  private final MfaFeatureService mfaFeatureService;
  private final FactorChallengeService factorChallengeService;
  private final Clock clock;

  public OperatorSetupService(
      PlatformOperatorRepository platformOperatorRepository,
      OperatorSetupTokenRepository operatorSetupTokenRepository,
      PasswordEncoder passwordEncoder,
      Sha256Hasher sha256Hasher,
      AuditService auditService,
      OperatorSessionService operatorSessionService,
      MfaFeatureService mfaFeatureService,
      FactorChallengeService factorChallengeService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.operatorSetupTokenRepository = operatorSetupTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.sha256Hasher = sha256Hasher;
    this.auditService = auditService;
    this.operatorSessionService = operatorSessionService;
    this.mfaFeatureService = mfaFeatureService;
    this.factorChallengeService = factorChallengeService;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public SetupBeginResult beginSetup(String setupToken, String password) {
    Instant now = Instant.now(clock);
    if (platformOperatorRepository.existsByStatus(OperatorStatus.ACTIVE)) {
      auditService.append(AuditService.OPERATOR_SETUP_REJECTED, null, "operator already active");
      throw new OperatorSetupRejectedException("operator already initialized");
    }

    OperatorSetupToken token = requireUsableToken(setupToken, now);
    if (token.hasBegun()) {
      auditService.append(AuditService.OPERATOR_SETUP_REJECTED, null, "setup token already used");
      throw new OperatorSetupRejectedException("operator setup already begun");
    }

    platformOperatorRepository.deleteAll();

    PlatformOperator operator =
        platformOperatorRepository.save(
            new PlatformOperator(
                passwordEncoder.encode(password), null, null, OperatorStatus.PENDING_TOTP));

    token.markBegun();
    auditService.append(AuditService.OPERATOR_SETUP_BEGUN, operator.id(), "operator setup begun");

    return new SetupBeginResult(null, null);
  }

  @Transactional
  public void completeSetup(String setupToken, String totpCode) {
    Instant now = Instant.now(clock);
    OperatorSetupToken token = requireUsableToken(setupToken, now);
    PlatformOperator operator = requireOperator();

    if (!token.hasBegun() || operator.status() != OperatorStatus.PENDING_TOTP) {
      auditService.append(AuditService.OPERATOR_SETUP_REJECTED, operator.id(), "setup not pending");
      throw new OperatorSetupRejectedException(HttpStatus.CONFLICT, "operator setup is not pending");
    }

    // Optional TOTP binding during setup is reserved for enroll endpoints when MfaFeature is on.
    // completeSetup always activates after password was set in begin.
    if (totpCode != null && !totpCode.isBlank() && operator.hasTotpBinding()) {
      throw new OperatorSetupRejectedException(HttpStatus.UNAUTHORIZED, "invalid TOTP code");
    }

    operator.activate();
    token.markCompleted();
    auditService.append(
        AuditService.OPERATOR_SETUP_COMPLETED, operator.id(), "operator setup completed");
  }

  @Transactional
  public LoginDecision login(String password) {
    PlatformOperator operator = requireOperator();

    if (operator.status() != OperatorStatus.ACTIVE
        || !passwordEncoder.matches(password, operator.passwordHash())) {
      auditService.append(AuditService.OPERATOR_LOGIN_FAILED, operator.id(), "login rejected");
      throw new OperatorAuthenticationException("invalid operator credentials");
    }

    if (mfaFeatureService.isEnabled() && operator.hasTotpBinding()) {
      return factorChallengeService.begin(SessionPrincipalKind.OPERATOR, operator.id());
    }

    auditService.append(AuditService.OPERATOR_LOGIN_SUCCEEDED, operator.id(), "login succeeded");
    return new LoginDecision.SessionReady(operator.id());
  }

  public OperatorLoginResult issueSession(java.util.UUID operatorId) {
    return operatorSessionService.createSession(operatorId);
  }

  private OperatorSetupToken requireUsableToken(String setupToken, Instant now) {
    return operatorSetupTokenRepository
        .findByTokenHash(sha256Hasher.hash(setupToken))
        .filter(token -> !token.isExpired(now))
        .filter(token -> !token.isCompleted())
        .orElseThrow(
            () -> {
              auditService.append(AuditService.OPERATOR_SETUP_REJECTED, null, "invalid setup token");
              return new OperatorSetupRejectedException(
                  HttpStatus.UNAUTHORIZED, "invalid setup token");
            });
  }

  private PlatformOperator requireOperator() {
    return platformOperatorRepository
        .findFirstByOrderByCreatedAtAsc()
        .orElseThrow(() -> new OperatorAuthenticationException("operator not initialized"));
  }

  public record SetupBeginResult(String totpSecret, String otpauthUrl) {}
}
