package io.rosecloud.iam.operator;

import io.rosecloud.iam.identity.FactorChallengeService;
import io.rosecloud.iam.identity.LoginDecision;
import io.rosecloud.iam.identity.MfaFeatureService;
import io.rosecloud.iam.identity.SessionPrincipalKind;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorStepUpService {

  private final PlatformOperatorRepository platformOperatorRepository;
  private final PasswordEncoder passwordEncoder;
  private final MfaFeatureService mfaFeatureService;
  private final FactorChallengeService factorChallengeService;

  public OperatorStepUpService(
      PlatformOperatorRepository platformOperatorRepository,
      PasswordEncoder passwordEncoder,
      MfaFeatureService mfaFeatureService,
      FactorChallengeService factorChallengeService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.passwordEncoder = passwordEncoder;
    this.mfaFeatureService = mfaFeatureService;
    this.factorChallengeService = factorChallengeService;
  }

  @Transactional
  public LoginDecision begin(UUID operatorId, String password) {
    PlatformOperator operator =
        platformOperatorRepository
            .findById(operatorId)
            .filter(candidate -> candidate.status() == OperatorStatus.ACTIVE)
            .orElseThrow(() -> new OperatorAuthenticationException("invalid operator credentials"));

    if (!passwordEncoder.matches(password, operator.passwordHash())) {
      throw new OperatorAuthenticationException("invalid operator credentials");
    }

    if (mfaFeatureService.isEnabled() && operator.hasTotpBinding()) {
      return factorChallengeService.begin(SessionPrincipalKind.OPERATOR, operator.id());
    }
    return new LoginDecision.SessionReady(operator.id());
  }
}
