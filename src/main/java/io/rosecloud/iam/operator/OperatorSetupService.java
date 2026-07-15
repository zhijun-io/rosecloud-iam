package io.rosecloud.iam.operator;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.TotpService;
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
  private final TotpService totpService;
  private final Sha256Hasher sha256Hasher;
  private final AuditService auditService;
  private final OperatorSessionService operatorSessionService;
  private final Clock clock;

  public OperatorSetupService(
      PlatformOperatorRepository platformOperatorRepository,
      OperatorSetupTokenRepository operatorSetupTokenRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      Sha256Hasher sha256Hasher,
      AuditService auditService,
      OperatorSessionService operatorSessionService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.operatorSetupTokenRepository = operatorSetupTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.sha256Hasher = sha256Hasher;
    this.auditService = auditService;
    this.operatorSessionService = operatorSessionService;
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

    // Replace any abandoned PENDING_TOTP row from a previous incomplete bootstrap.
    platformOperatorRepository.deleteAll();

    TotpService.TotpEnrollment enrollment = totpService.newEnrollment("platform-operator");
    PlatformOperator operator =
        platformOperatorRepository.save(
            new PlatformOperator(
                passwordEncoder.encode(password),
                enrollment.encryptedSecret().ciphertext(),
                enrollment.encryptedSecret().keyId(),
                OperatorStatus.PENDING_TOTP));

    token.markBegun();
    auditService.append(AuditService.OPERATOR_SETUP_BEGUN, operator.id(), "operator setup begun");

    return new SetupBeginResult(enrollment.secret(), enrollment.otpauthUrl());
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

    if (!totpService.verify(operator.totpSecretKeyId(), operator.totpSecretCiphertext(), totpCode)) {
      auditService.append(AuditService.OPERATOR_SETUP_REJECTED, operator.id(), "invalid totp");
      throw new OperatorSetupRejectedException(HttpStatus.UNAUTHORIZED, "invalid TOTP code");
    }

    operator.activate();
    token.markCompleted();
    auditService.append(
        AuditService.OPERATOR_SETUP_COMPLETED, operator.id(), "operator setup completed");
  }

  @Transactional
  public OperatorLoginResult login(String password, String totpCode) {
    PlatformOperator operator = requireOperator();

    if (operator.status() != OperatorStatus.ACTIVE
        || !passwordEncoder.matches(password, operator.passwordHash())
        || !totpService.verify(operator.totpSecretKeyId(), operator.totpSecretCiphertext(), totpCode)) {
      auditService.append(AuditService.OPERATOR_LOGIN_FAILED, operator.id(), "login rejected");
      throw new OperatorAuthenticationException("invalid operator credentials");
    }

    OperatorLoginResult result = operatorSessionService.createSession(operator.id());
    auditService.append(AuditService.OPERATOR_LOGIN_SUCCEEDED, operator.id(), "login succeeded");
    return result;
  }

  private OperatorSetupToken requireUsableToken(String setupToken, Instant now) {
    return operatorSetupTokenRepository
        .findByTokenHash(sha256Hasher.hash(setupToken))
        .filter(token -> !token.isExpired(now))
        .filter(token -> !token.isCompleted())
        .orElseThrow(
            () -> {
              auditService.append(AuditService.OPERATOR_SETUP_REJECTED, null, "invalid setup token");
              return new OperatorSetupRejectedException(HttpStatus.UNAUTHORIZED, "invalid setup token");
            });
  }

  private PlatformOperator requireOperator() {
    return platformOperatorRepository
        .findFirstByOrderByCreatedAtAsc()
        .orElseThrow(() -> new OperatorAuthenticationException("operator not initialized"));
  }

  public record SetupBeginResult(String totpSecret, String otpauthUrl) {}
}
