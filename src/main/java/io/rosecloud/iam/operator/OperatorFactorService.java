package io.rosecloud.iam.operator;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.identity.FactorChallengeException;
import io.rosecloud.iam.identity.MfaFeatureService;
import io.rosecloud.iam.identity.RecoveryCodeService;
import io.rosecloud.iam.identity.SessionPrincipalKind;
import io.rosecloud.iam.identity.TotpService;
import io.rosecloud.iam.shared.TotpSecretCrypto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorFactorService {

  private final PlatformOperatorRepository platformOperatorRepository;
  private final TotpService totpService;
  private final MfaFeatureService mfaFeatureService;
  private final RecoveryCodeService recoveryCodeService;
  private final AuditService auditService;

  public OperatorFactorService(
      PlatformOperatorRepository platformOperatorRepository,
      TotpService totpService,
      MfaFeatureService mfaFeatureService,
      RecoveryCodeService recoveryCodeService,
      AuditService auditService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.totpService = totpService;
    this.mfaFeatureService = mfaFeatureService;
    this.recoveryCodeService = recoveryCodeService;
    this.auditService = auditService;
  }

  @Transactional
  public TotpService.TotpEnrollment beginTotp(UUID operatorId) {
    requireMfaOnForEnrollment();
    PlatformOperator operator = requireActive(operatorId);
    if (operator.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "factor binding already present");
    }
    TotpService.TotpEnrollment enrollment = totpService.newEnrollment("platform-operator");
    TotpSecretCrypto.EncryptedSecret encrypted = enrollment.encryptedSecret();
    operator.beginPendingTotp(encrypted.ciphertext(), encrypted.keyId());
    return enrollment;
  }

  @Transactional
  public List<String> completeTotp(UUID operatorId, String totpCode) {
    requireMfaOnForEnrollment();
    PlatformOperator operator = requireActive(operatorId);
    if (!operator.hasPendingTotp()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no pending enrollment");
    }
    if (!totpService.verify(
        operator.pendingTotpSecretKeyId(),
        operator.pendingTotpSecretCiphertext(),
        totpCode)) {
      throw new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid TOTP code");
    }
    operator.bindTotp(operator.pendingTotpSecretCiphertext(), operator.pendingTotpSecretKeyId());
    List<String> recoveryCodes =
        recoveryCodeService.replaceAll(SessionPrincipalKind.OPERATOR, operatorId);
    auditService.append("factor.binding_created", operatorId, "operator totp binding created");
    return recoveryCodes;
  }

  @Transactional
  public void revokeTotp(UUID operatorId) {
    PlatformOperator operator = requireActive(operatorId);
    if (!operator.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no factor binding");
    }
    operator.clearTotp();
    recoveryCodeService.revokeAll(SessionPrincipalKind.OPERATOR, operatorId);
    auditService.append("factor.binding_revoked", operatorId, "operator totp binding revoked");
  }

  @Transactional
  public List<String> regenerateRecoveryCodes(UUID operatorId) {
    PlatformOperator operator = requireActive(operatorId);
    if (!operator.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no factor binding");
    }
    List<String> codes =
        recoveryCodeService.replaceAll(SessionPrincipalKind.OPERATOR, operatorId);
    auditService.append(
        "recovery_code.regenerated", operatorId, "operator recovery codes regenerated");
    return codes;
  }

  private void requireMfaOnForEnrollment() {
    if (!mfaFeatureService.isEnabled()) {
      throw new FactorChallengeException(
          HttpStatus.CONFLICT, "mfa feature disabled; cannot create factor binding");
    }
  }

  private PlatformOperator requireActive(UUID operatorId) {
    return platformOperatorRepository
        .findById(operatorId)
        .filter(op -> op.status() == OperatorStatus.ACTIVE)
        .orElseThrow(
            () -> new FactorChallengeException(HttpStatus.UNAUTHORIZED, "operator not found"));
  }
}
