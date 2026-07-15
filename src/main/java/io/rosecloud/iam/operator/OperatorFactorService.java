package io.rosecloud.iam.operator;

import io.rosecloud.iam.access.SessionRevocationPort;
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
  private final SessionRevocationPort sessionRevocationPort;
  private final AuditService auditService;

  public OperatorFactorService(
      PlatformOperatorRepository platformOperatorRepository,
      TotpService totpService,
      MfaFeatureService mfaFeatureService,
      RecoveryCodeService recoveryCodeService,
      SessionRevocationPort sessionRevocationPort,
      AuditService auditService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.totpService = totpService;
    this.mfaFeatureService = mfaFeatureService;
    this.recoveryCodeService = recoveryCodeService;
    this.sessionRevocationPort = sessionRevocationPort;
    this.auditService = auditService;
  }

  @Transactional
  public TotpService.TotpBindMaterial beginTotp(UUID operatorId) {
    requireMfaOnForBinding();
    PlatformOperator operator = requireActive(operatorId);
    if (operator.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "factor binding already present");
    }
    TotpService.TotpBindMaterial material = totpService.beginBind("platform-operator");
    TotpSecretCrypto.EncryptedSecret encrypted = material.encryptedSecret();
    operator.beginPendingTotp(encrypted.ciphertext(), encrypted.keyId());
    return material;
  }

  @Transactional
  public List<String> completeTotp(UUID operatorId, String totpCode) {
    requireMfaOnForBinding();
    PlatformOperator operator = requireActive(operatorId);
    if (!operator.hasPendingTotp()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no pending factor binding");
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
    sessionRevocationPort.revokeAllForPrincipal("OPERATOR", operatorId);
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
    sessionRevocationPort.revokeAllForPrincipal("OPERATOR", operatorId);
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
    sessionRevocationPort.revokeAllForPrincipal("OPERATOR", operatorId);
    auditService.append(
        "recovery_code.regenerated", operatorId, "operator recovery codes regenerated");
    return codes;
  }

  @Transactional
  public void resetCredentials(UUID operatorId, String reason) {
    PlatformOperator operator =
        platformOperatorRepository
            .findById(operatorId)
            .orElseThrow(
                () -> new FactorChallengeException(HttpStatus.NOT_FOUND, "operator not found"));
    operator.clearTotp();
    recoveryCodeService.revokeAll(SessionPrincipalKind.OPERATOR, operatorId);
    sessionRevocationPort.revokeAllForPrincipal("OPERATOR", operatorId);
    auditService.append(
        "factor.operator_reset",
        operatorId,
        "operator reset mfa credentials; reason=" + reason);
  }

  private void requireMfaOnForBinding() {
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
