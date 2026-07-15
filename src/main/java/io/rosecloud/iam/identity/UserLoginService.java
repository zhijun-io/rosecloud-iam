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
  private final AuditService auditService;
  private final LoginRateLimiter loginRateLimiter;
  private final MfaFeatureService mfaFeatureService;
  private final FactorChallengeService factorChallengeService;

  public UserLoginService(
      IamUserRepository iamUserRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      LoginRateLimiter loginRateLimiter,
      MfaFeatureService mfaFeatureService,
      FactorChallengeService factorChallengeService) {
    this.iamUserRepository = iamUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.loginRateLimiter = loginRateLimiter;
    this.mfaFeatureService = mfaFeatureService;
    this.factorChallengeService = factorChallengeService;
  }

  @Transactional
  public LoginDecision login(String email, String password, String clientIp) {
    loginRateLimiter.assertAllowed(email, clientIp);
    try {
      LoginDecision decision = verify(email, password);
      if (decision instanceof LoginDecision.SessionReady) {
        loginRateLimiter.recordSuccess(email, clientIp);
      }
      return decision;
    } catch (UserAuthenticationException exception) {
      loginRateLimiter.recordFailure(email, clientIp);
      throw exception;
    }
  }

  private LoginDecision verify(String email, String password) {
    IamUser user =
        iamUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT)).orElse(null);

    if (user == null
        || user.status() != UserStatus.ACTIVE
        || !passwordEncoder.matches(password, user.passwordHash())) {
      auditService.append(
          AuditService.USER_LOGIN_FAILED, user == null ? null : user.id(), "login rejected");
      throw new UserAuthenticationException(GENERIC_FAILURE);
    }

    if (mfaFeatureService.isEnabled() && user.hasTotpBinding()) {
      return factorChallengeService.begin(SessionPrincipalKind.USER, user.id());
    }

    auditService.append(AuditService.USER_LOGIN_SUCCEEDED, user.id(), "login succeeded");
    return new LoginDecision.SessionReady(user.id());
  }
}
