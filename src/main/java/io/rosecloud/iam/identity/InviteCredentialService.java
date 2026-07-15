package io.rosecloud.iam.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit identity contract for invitation flows. Tenancy must not touch User Entity/Repository
 * directly (architecture §2).
 */
@Service
public class InviteCredentialService {

  public static final String GENERIC_REJECTION = "invitation cannot be accepted";

  private final IamUserRepository iamUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;

  public InviteCredentialService(
      IamUserRepository iamUserRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService) {
    this.iamUserRepository = iamUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
  }

  @Transactional
  public EnrollmentBegin beginEnrollment(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    TotpService.TotpEnrollment enrollment = totpService.newEnrollment(normalized);

    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(normalized)
            .map(existing -> replacePendingEnrollment(existing, password, enrollment))
            .orElseGet(
                () ->
                    new IamUser(
                        normalized,
                        passwordEncoder.encode(password),
                        enrollment.encryptedSecret().ciphertext(),
                        enrollment.encryptedSecret().keyId(),
                        UserStatus.PENDING_TOTP));
    iamUserRepository.save(user);
    return new EnrollmentBegin(enrollment.secret(), enrollment.otpauthUrl());
  }

  @Transactional
  public UUID activatePendingWithTotp(String email, String totpCode) {
    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(email)
            .filter(candidate -> candidate.status() == UserStatus.PENDING_TOTP)
            .orElseThrow(() -> new InviteCredentialException(GENERIC_REJECTION));

    if (!totpService.verify(user.totpSecretKeyId(), user.totpSecretCiphertext(), totpCode)) {
      throw new InviteCredentialException("invalid TOTP code");
    }

    user.activate();
    return user.id();
  }

  @Transactional(readOnly = true)
  public UUID authenticateActiveUser(String email, String password, String totpCode) {
    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(email)
            .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
            .orElseThrow(() -> new InviteCredentialException(GENERIC_REJECTION));

    if (!passwordEncoder.matches(password, user.passwordHash())
        || !totpService.verify(user.totpSecretKeyId(), user.totpSecretCiphertext(), totpCode)) {
      throw new InviteCredentialException(GENERIC_REJECTION);
    }

    return user.id();
  }

  private IamUser replacePendingEnrollment(
      IamUser existing, String password, TotpService.TotpEnrollment enrollment) {
    if (existing.status() != UserStatus.PENDING_TOTP) {
      throw new InviteCredentialException(GENERIC_REJECTION);
    }

    existing.replacePendingEnrollment(
        passwordEncoder.encode(password),
        enrollment.encryptedSecret().ciphertext(),
        enrollment.encryptedSecret().keyId());
    return existing;
  }

  public record EnrollmentBegin(String totpSecret, String otpauthUrl) {}
}
