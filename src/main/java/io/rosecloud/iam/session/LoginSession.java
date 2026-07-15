package io.rosecloud.iam.session;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_session")
class LoginSession {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "principal_type", nullable = false, length = 16)
  private SessionPrincipalType principalType;

  @Column(name = "principal_id", nullable = false)
  private UUID principalId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "refresh_token_hash", nullable = false, length = 64)
  private String refreshTokenHash;

  @Column(name = "previous_refresh_token_hash", length = 64)
  private String previousRefreshTokenHash;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "step_up_satisfied_at")
  private Instant stepUpSatisfiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LoginSession() {}

  LoginSession(
      SessionPrincipalType principalType,
      UUID principalId,
      UUID familyId,
      String refreshTokenHash,
      Instant expiresAt,
      Instant stepUpSatisfiedAt) {
    this.id = UuidV7.next();
    this.principalType = principalType;
    this.principalId = principalId;
    this.familyId = familyId;
    this.refreshTokenHash = refreshTokenHash;
    this.expiresAt = expiresAt;
    this.stepUpSatisfiedAt = stepUpSatisfiedAt;
  }

  static LoginSession operator(
      UUID operatorId, String refreshTokenHash, Instant expiresAt, Instant stepUpSatisfiedAt) {
    return new LoginSession(
        SessionPrincipalType.OPERATOR,
        operatorId,
        UuidV7.next(),
        refreshTokenHash,
        expiresAt,
        stepUpSatisfiedAt);
  }

  static LoginSession user(
      UUID userId,
      UUID familyId,
      String refreshTokenHash,
      Instant expiresAt,
      Instant stepUpSatisfiedAt) {
    return new LoginSession(
        SessionPrincipalType.USER, userId, familyId, refreshTokenHash, expiresAt, stepUpSatisfiedAt);
  }

  Instant stepUpSatisfiedAt() {
    return stepUpSatisfiedAt;
  }

  SessionPrincipalType principalType() {
    return principalType;
  }

  UUID principalId() {
    return principalId;
  }

  UUID familyId() {
    return familyId;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant rotatedAt() {
    return rotatedAt;
  }

  Instant revokedAt() {
    return revokedAt;
  }

  boolean matchesCurrentRefreshHash(String refreshTokenHash) {
    return this.refreshTokenHash.equals(refreshTokenHash);
  }

  boolean matchesPreviousRefreshHash(String refreshTokenHash) {
    return previousRefreshTokenHash != null && previousRefreshTokenHash.equals(refreshTokenHash);
  }

  boolean isRevoked() {
    return revokedAt != null;
  }

  boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  void rotate(String nextRefreshTokenHash, Instant rotatedAt, Instant nextExpiresAt) {
    this.previousRefreshTokenHash = refreshTokenHash;
    this.refreshTokenHash = nextRefreshTokenHash;
    this.rotatedAt = rotatedAt;
    this.expiresAt = nextExpiresAt;
  }

  void revoke(Instant revokedAt) {
    if (this.revokedAt == null) {
      this.revokedAt = revokedAt;
    }
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
