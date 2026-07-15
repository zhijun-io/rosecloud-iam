package io.rosecloud.iam.session;

import io.rosecloud.iam.audit.AuditService;
import io.rosecloud.iam.bootstrap.JwtIssuer;
import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import io.rosecloud.iam.shared.Sha256Hasher;
import io.rosecloud.iam.shared.UuidV7;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSessionService {

  private final LoginSessionRepository loginSessionRepository;
  private final JwtIssuer jwtIssuer;
  private final RosecloudIamProperties properties;
  private final Sha256Hasher sha256Hasher;
  private final AuditService auditService;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public UserSessionService(
      LoginSessionRepository loginSessionRepository,
      JwtIssuer jwtIssuer,
      RosecloudIamProperties properties,
      Sha256Hasher sha256Hasher,
      AuditService auditService) {
    this.loginSessionRepository = loginSessionRepository;
    this.jwtIssuer = jwtIssuer;
    this.properties = properties;
    this.sha256Hasher = sha256Hasher;
    this.auditService = auditService;
    this.clock = Clock.systemUTC();
  }

  @Transactional
  public UserLoginResult createSession(UUID userId) {
    String refreshToken = randomRefreshToken();
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.refreshTokenTtl());

    loginSessionRepository.save(
        LoginSession.user(userId, UuidV7.next(), sha256Hasher.hash(refreshToken), expiresAt, now));
    pruneOldestSessions(userId);

    JwtIssuer.IssuedAccessToken accessToken = jwtIssuer.issueUserToken(userId);
    return new UserLoginResult(
        accessToken.value(), refreshToken, accessToken.expiresInSeconds());
  }

  @Transactional(noRollbackFor = SessionException.class)
  public UserRefreshResult refresh(String refreshToken) {
    Instant now = Instant.now(clock);
    String refreshTokenHash = sha256Hasher.hash(refreshToken);
    LoginSession session = findRefreshSession(refreshTokenHash);

    if (session.principalType() != SessionPrincipalType.USER || session.isRevoked() || session.isExpired(now)) {
      throw new SessionException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }

    if (session.matchesPreviousRefreshHash(refreshTokenHash)) {
      Instant graceDeadline = session.rotatedAt().plus(properties.refreshReuseGrace());
      if (!now.isAfter(graceDeadline)) {
        throw new SessionException(HttpStatus.CONFLICT, "refresh_in_progress");
      }

      revokeFamily(session.familyId(), now);
      auditService.append(
          AuditService.USER_REFRESH_REUSE_REVOKED,
          session.principalId(),
          "stale refresh token reused outside grace window");
      throw new SessionException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }

    String nextRefreshToken = randomRefreshToken();
    session.rotate(
        sha256Hasher.hash(nextRefreshToken), now, now.plus(properties.refreshTokenTtl()));

    JwtIssuer.IssuedAccessToken accessToken = jwtIssuer.issueUserToken(session.principalId());
    auditService.append(
        AuditService.USER_REFRESH_SUCCEEDED, session.principalId(), "refresh token rotated");
    return new UserRefreshResult(
        accessToken.value(), nextRefreshToken, accessToken.expiresInSeconds());
  }

  @Transactional
  public void logout(String refreshToken, UUID userId) {
    Instant now = Instant.now(clock);
    UUID actorId = userId;

    if (refreshToken != null && !refreshToken.isBlank()) {
      LoginSession session = findRefreshSession(sha256Hasher.hash(refreshToken));
      revokeFamily(session.familyId(), now);
      actorId = session.principalId();
    } else if (userId != null) {
      List<LoginSession> sessions =
          loginSessionRepository.findByPrincipalTypeAndPrincipalIdAndRevokedAtIsNullOrderByCreatedAtAsc(
              SessionPrincipalType.USER, userId);
      for (LoginSession session : sessions) {
        session.revoke(now);
      }
    } else {
      throw new SessionException(HttpStatus.UNAUTHORIZED, "refresh token or bearer token required");
    }

    auditService.append(AuditService.USER_LOGOUT, actorId, "session logout");
  }

  private LoginSession findRefreshSession(String refreshTokenHash) {
    return loginSessionRepository
        .findByRefreshTokenHash(refreshTokenHash)
        .or(() -> loginSessionRepository.findByPreviousRefreshTokenHash(refreshTokenHash))
        .orElseThrow(() -> new SessionException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
  }

  private void pruneOldestSessions(UUID userId) {
    List<LoginSession> sessions =
        loginSessionRepository.findByPrincipalTypeAndPrincipalIdAndRevokedAtIsNullOrderByCreatedAtAsc(
            SessionPrincipalType.USER, userId);
    int pruneCount = sessions.size() - properties.maxSessionsPerUser();
    if (pruneCount <= 0) {
      return;
    }

    Instant now = Instant.now(clock);
    for (int index = 0; index < pruneCount; index++) {
      sessions.get(index).revoke(now);
    }
  }

  private void revokeFamily(UUID familyId, Instant revokedAt) {
    loginSessionRepository.revokeFamily(familyId, revokedAt);
  }

  private String randomRefreshToken() {
    byte[] rawRefreshTokenBytes = new byte[32];
    secureRandom.nextBytes(rawRefreshTokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(rawRefreshTokenBytes);
  }
}
