package io.rosecloud.iam.session;

import io.rosecloud.iam.bootstrap.JwtIssuer;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import io.rosecloud.iam.shared.Sha256Hasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorSessionService {

  private final LoginSessionRepository loginSessionRepository;
  private final JwtIssuer jwtIssuer;
  private final RosecloudIamProperties properties;
  private final Sha256Hasher sha256Hasher;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public OperatorSessionService(
      LoginSessionRepository loginSessionRepository,
      JwtIssuer jwtIssuer,
      RosecloudIamProperties properties,
      Sha256Hasher sha256Hasher) {
    this.loginSessionRepository = loginSessionRepository;
    this.jwtIssuer = jwtIssuer;
    this.properties = properties;
    this.sha256Hasher = sha256Hasher;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public OperatorLoginResult createSession(UUID operatorId) {
    byte[] rawRefreshTokenBytes = new byte[32];
    secureRandom.nextBytes(rawRefreshTokenBytes);
    String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawRefreshTokenBytes);

    Instant expiresAt = Instant.now(clock).plus(properties.refreshTokenTtl());
    loginSessionRepository.save(
        LoginSession.operator(operatorId, sha256Hasher.hash(refreshToken), expiresAt));

    JwtIssuer.IssuedAccessToken accessToken = jwtIssuer.issueOperatorToken(operatorId);
    return new OperatorLoginResult(
        accessToken.value(), refreshToken, accessToken.expiresInSeconds());
  }
}
