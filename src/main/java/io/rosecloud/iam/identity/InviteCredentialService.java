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
  private final MfaFeatureService mfaFeatureService;
  private final FactorChallengeService factorChallengeService;

  public InviteCredentialService(
      IamUserRepository iamUserRepository,
      PasswordEncoder passwordEncoder,
      MfaFeatureService mfaFeatureService,
      FactorChallengeService factorChallengeService) {
    this.iamUserRepository = iamUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.mfaFeatureService = mfaFeatureService;
    this.factorChallengeService = factorChallengeService;
  }

  @Transactional
  public EnrollmentBegin beginEnrollment(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);

    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(normalized)
            .map(existing -> replacePendingEnrollment(existing, password))
            .orElseGet(
                () ->
                    new IamUser(
                        normalized,
                        passwordEncoder.encode(password),
                        null,
                        null,
                        UserStatus.PENDING_TOTP));
    iamUserRepository.save(user);
    return new EnrollmentBegin(null, null);
  }

  @Transactional
  public UUID activatePending(String email) {
    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(email)
            .filter(candidate -> candidate.status() == UserStatus.PENDING_TOTP)
            .orElseThrow(() -> new InviteCredentialException(GENERIC_REJECTION));

    user.activate();
    return user.id();
  }

  /** @deprecated use {@link #activatePending(String)} — totp no longer required when MfaFeature off */
  @Transactional
  public UUID activatePendingWithTotp(String email, String totpCode) {
    return activatePending(email);
  }

  @Transactional
  public LoginDecision authenticateActiveUser(String email, String password) {
    IamUser user =
        iamUserRepository
            .findByEmailIgnoreCase(email)
            .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
            .orElseThrow(() -> new InviteCredentialException(GENERIC_REJECTION));

    if (!passwordEncoder.matches(password, user.passwordHash())) {
      throw new InviteCredentialException(GENERIC_REJECTION);
    }

    if (mfaFeatureService.isEnabled() && user.hasTotpBinding()) {
      return factorChallengeService.begin(SessionPrincipalKind.USER, user.id());
    }

    return new LoginDecision.SessionReady(user.id());
  }

  private IamUser replacePendingEnrollment(IamUser existing, String password) {
    if (existing.status() != UserStatus.PENDING_TOTP) {
      throw new InviteCredentialException(GENERIC_REJECTION);
    }

    existing.replacePendingEnrollment(passwordEncoder.encode(password));
    return existing;
  }

  public record EnrollmentBegin(String totpSecret, String otpauthUrl) {}
}
