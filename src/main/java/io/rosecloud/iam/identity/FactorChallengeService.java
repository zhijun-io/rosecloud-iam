package io.rosecloud.iam.identity;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactorChallengeService {

  private final FactorChallengeRepository factorChallengeRepository;
  private final IamUserRepository iamUserRepository;
  private final OperatorTotpBindingPort operatorTotpBindingPort;
  private final TotpService totpService;
  private final RecoveryCodeService recoveryCodeService;
  private final LoginRateLimiter loginRateLimiter;
  private final RosecloudIamProperties properties;
  private final Clock clock = Clock.systemUTC();

  public FactorChallengeService(
      FactorChallengeRepository factorChallengeRepository,
      IamUserRepository iamUserRepository,
      OperatorTotpBindingPort operatorTotpBindingPort,
      TotpService totpService,
      RecoveryCodeService recoveryCodeService,
      LoginRateLimiter loginRateLimiter,
      RosecloudIamProperties properties) {
    this.factorChallengeRepository = factorChallengeRepository;
    this.iamUserRepository = iamUserRepository;
    this.operatorTotpBindingPort = operatorTotpBindingPort;
    this.totpService = totpService;
    this.recoveryCodeService = recoveryCodeService;
    this.loginRateLimiter = loginRateLimiter;
    this.properties = properties;
  }

  @Transactional
  public LoginDecision.ChallengeRequired begin(SessionPrincipalKind kind, UUID principalId) {
    List<FactorBindingView> bindings = listBindings(kind, principalId);
    if (bindings.isEmpty()) {
      throw new FactorChallengeException(HttpStatus.CONFLICT, "no factor bindings");
    }
    Instant expiresAt = Instant.now(clock).plus(properties.factorChallengeTtl());
    FactorChallenge challenge =
        factorChallengeRepository.save(
            new FactorChallenge(kind, principalId, bindings.getFirst().kind(), expiresAt));
    return new LoginDecision.ChallengeRequired(challenge.id(), bindings);
  }

  @Transactional
  public UUID verify(
      UUID challengeId, String bindingId, String totpCode, String recoveryCode, String clientIp) {
    Instant now = Instant.now(clock);
    FactorChallenge challenge =
        factorChallengeRepository
            .findById(challengeId)
            .filter(c -> c.isUsable(now))
            .orElseThrow(
                () -> new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid challenge"));

    String rateLimitKey = rateLimitKey(challenge.principalType(), challenge.principalId());
    loginRateLimiter.assertAllowed(rateLimitKey, clientIp);

    try {
      if (recoveryCode != null && !recoveryCode.isBlank()) {
        if (!recoveryCodeService.consume(
            challenge.principalType(), challenge.principalId(), recoveryCode)) {
          throw new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid challenge");
        }
      } else {
        String expectedBindingId =
            FactorBindingView.totpBindingId(challenge.principalId()).toString();
        if (bindingId == null || bindingId.isBlank() || !bindingId.equals(expectedBindingId)) {
          throw new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid challenge");
        }
        if (totpCode == null
            || !verifyTotp(challenge.principalType(), challenge.principalId(), totpCode)) {
          throw new FactorChallengeException(HttpStatus.UNAUTHORIZED, "invalid challenge");
        }
      }
    } catch (FactorChallengeException exception) {
      loginRateLimiter.recordFailure(rateLimitKey, clientIp);
      throw exception;
    }

    challenge.consume(now);
    loginRateLimiter.recordSuccess(rateLimitKey, clientIp);
    return challenge.principalId();
  }

  public List<FactorBindingView> listBindings(SessionPrincipalKind kind, UUID principalId) {
    return switch (kind) {
      case USER -> iamUserRepository
          .findById(principalId)
          .filter(IamUser::hasTotpBinding)
          .map(u -> List.of(FactorBindingView.totp(u.id())))
          .orElse(List.of());
      case OPERATOR -> {
        if (!operatorTotpBindingPort.hasBinding(principalId)) {
          yield List.of();
        }
        yield List.of(FactorBindingView.totp(principalId));
      }
    };
  }

  private String rateLimitKey(SessionPrincipalKind kind, UUID principalId) {
    return switch (kind) {
      case USER -> iamUserRepository
          .findById(principalId)
          .map(IamUser::email)
          .orElse(principalId.toString());
      case OPERATOR -> "operator:" + principalId;
    };
  }

  private boolean verifyTotp(SessionPrincipalKind kind, UUID principalId, String totpCode) {
    return switch (kind) {
      case USER -> iamUserRepository
          .findById(principalId)
          .filter(IamUser::hasTotpBinding)
          .map(u -> totpService.verify(u.totpSecretKeyId(), u.totpSecretCiphertext(), totpCode))
          .orElse(false);
      case OPERATOR -> operatorTotpBindingPort.verifyTotp(principalId, totpCode);
    };
  }
}
