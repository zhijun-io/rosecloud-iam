package io.rosecloud.iam.identity;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserStepUpService {

  private final IamUserRepository iamUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final MfaFeatureService mfaFeatureService;
  private final FactorChallengeService factorChallengeService;

  public UserStepUpService(
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
  public LoginDecision begin(UUID userId, String password) {
    IamUser user =
        iamUserRepository
            .findById(userId)
            .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
            .orElseThrow(() -> new UserAuthenticationException("invalid user credentials"));

    if (!passwordEncoder.matches(password, user.passwordHash())) {
      throw new UserAuthenticationException("invalid user credentials");
    }

    if (mfaFeatureService.isEnabled() && user.hasTotpBinding()) {
      return factorChallengeService.begin(SessionPrincipalKind.USER, user.id());
    }
    return new LoginDecision.SessionReady(user.id());
  }
}
