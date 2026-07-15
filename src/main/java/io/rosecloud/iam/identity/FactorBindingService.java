package io.rosecloud.iam.identity;

import io.rosecloud.iam.access.SessionRevocationPort;
import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.shared.TotpSecretCrypto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactorBindingService {

  private final IamUserRepository iamUserRepository;
  private final TotpService totpService;
  private final MfaFeatureService mfaFeatureService;
  private final RecoveryCodeService recoveryCodeService;
  private final SessionRevocationPort sessionRevocationPort;
  private final AuditService auditService;

  public FactorBindingService(
      IamUserRepository iamUserRepository,
      TotpService totpService,
      MfaFeatureService mfaFeatureService,
      RecoveryCodeService recoveryCodeService,
      SessionRevocationPort sessionRevocationPort,
      AuditService auditService) {
    this.iamUserRepository = iamUserRepository;
    this.totpService = totpService;
    this.mfaFeatureService = mfaFeatureService;
    this.recoveryCodeService = recoveryCodeService;
    this.sessionRevocationPort = sessionRevocationPort;
    this.auditService = auditService;
  }

  @Transactional
  public TotpService.TotpBindMaterial beginTotp(UUID userId) {
    requireMfaOnForBinding();
    IamUser user = requireActive(userId);
    if (user.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "factor binding already present");
    }
    TotpService.TotpBindMaterial material = totpService.beginBind(user.email());
    TotpSecretCrypto.EncryptedSecret encrypted = material.encryptedSecret();
    user.beginPendingTotp(encrypted.ciphertext(), encrypted.keyId());
    return material;
  }

  @Transactional
  public List<String> completeTotp(UUID userId, String totpCode) {
    requireMfaOnForBinding();
    IamUser user = requireActive(userId);
    if (!user.hasPendingTotp()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no pending factor binding");
    }
    if (!totpService.verify(
        user.pendingTotpSecretKeyId(), user.pendingTotpSecretCiphertext(), totpCode)) {
      throw new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid TOTP code");
    }
    user.bindTotp(user.pendingTotpSecretCiphertext(), user.pendingTotpSecretKeyId());
    List<String> recoveryCodes =
        recoveryCodeService.replaceAll(SessionPrincipalKind.USER, userId);
    sessionRevocationPort.revokeAllForPrincipal("USER", userId);
    auditService.append("factor.binding_created", userId, "totp binding created");
    return recoveryCodes;
  }

  @Transactional
  public void revokeTotp(UUID userId) {
    IamUser user = requireActive(userId);
    if (!user.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no factor binding");
    }
    user.clearTotp();
    recoveryCodeService.revokeAll(SessionPrincipalKind.USER, userId);
    sessionRevocationPort.revokeAllForPrincipal("USER", userId);
    auditService.append("factor.binding_revoked", userId, "totp binding revoked");
  }

  @Transactional
  public List<String> regenerateRecoveryCodes(UUID userId) {
    IamUser user = requireActive(userId);
    if (!user.hasTotpBinding()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no factor binding");
    }
    List<String> codes = recoveryCodeService.replaceAll(SessionPrincipalKind.USER, userId);
    sessionRevocationPort.revokeAllForPrincipal("USER", userId);
    auditService.append("recovery_code.regenerated", userId, "recovery codes regenerated");
    return codes;
  }

  @Transactional
  public void operatorReset(UUID userId, String reason) {
    IamUser user =
        iamUserRepository
            .findById(userId)
            .orElseThrow(() -> new FactorChallengeException(HttpStatus.NOT_FOUND, "user not found"));
    user.clearTotp();
    recoveryCodeService.revokeAll(SessionPrincipalKind.USER, userId);
    sessionRevocationPort.revokeAllForPrincipal("USER", userId);
    auditService.append(
        "factor.operator_reset", userId, "operator reset mfa credentials; reason=" + reason);
  }

  private void requireMfaOnForBinding() {
    if (!mfaFeatureService.isEnabled()) {
      throw new FactorChallengeException(
          HttpStatus.CONFLICT, "mfa feature disabled; cannot create factor binding");
    }
  }

  private IamUser requireActive(UUID userId) {
    return iamUserRepository
        .findById(userId)
        .filter(user -> user.status() == UserStatus.ACTIVE)
        .orElseThrow(() -> new FactorChallengeException(HttpStatus.UNAUTHORIZED, "user not found"));
  }
}
