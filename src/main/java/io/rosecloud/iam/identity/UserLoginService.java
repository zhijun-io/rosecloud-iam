package io.rosecloud.iam.identity;

import io.rosecloud.iam.audit.AuditService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLoginService {

  private static final String GENERIC_FAILURE = "invalid user credentials";

  private final IamUserRepository iamUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final AuditService auditService;
  private final LoginRateLimiter loginRateLimiter;

  public UserLoginService(
      IamUserRepository iamUserRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      AuditService auditService,
      LoginRateLimiter loginRateLimiter) {
    this.iamUserRepository = iamUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.auditService = auditService;
    this.loginRateLimiter = loginRateLimiter;
  }

  /**
   * Validates credentials and returns the authenticated User id. Session creation stays in session.
   * Applies progressive login cooldown keyed by email + client IP.
   */
  @Transactional(readOnly = true)
  public UUID authenticate(String email, String password, String totpCode, String clientIp) {
    loginRateLimiter.assertAllowed(email, clientIp);
    try {
      UUID userId = verifyCredentials(email, password, totpCode);
      loginRateLimiter.recordSuccess(email, clientIp);
      return userId;
    } catch (UserAuthenticationException exception) {
      loginRateLimiter.recordFailure(email, clientIp);
      throw exception;
    }
  }

  private UUID verifyCredentials(String email, String password, String totpCode) {
    IamUser user =
        iamUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT)).orElse(null);

    if (user == null
        || user.status() != UserStatus.ACTIVE
        || !passwordEncoder.matches(password, user.passwordHash())
        || !totpService.verify(user.totpSecretKeyId(), user.totpSecretCiphertext(), totpCode)) {
      auditService.append(
          AuditService.USER_LOGIN_FAILED, user == null ? null : user.id(), "login rejected");
      throw new UserAuthenticationException(GENERIC_FAILURE);
    }

    auditService.append(AuditService.USER_LOGIN_SUCCEEDED, user.id(), "login succeeded");
    return user.id();
  }
}
