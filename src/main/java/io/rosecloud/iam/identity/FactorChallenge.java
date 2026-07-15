package io.rosecloud.iam.identity;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "factor_challenge")
class FactorChallenge {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "principal_type", nullable = false, length = 16)
  private SessionPrincipalKind principalType;

  @Column(name = "principal_id", nullable = false)
  private UUID principalId;

  @Column(name = "factor_kind", nullable = false, length = 32)
  private String factorKind;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected FactorChallenge() {}

  FactorChallenge(
      SessionPrincipalKind principalType,
      UUID principalId,
      String factorKind,
      Instant expiresAt) {
    this.id = UuidV7.next();
    this.principalType = principalType;
    this.principalId = principalId;
    this.factorKind = factorKind;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  UUID id() {
    return id;
  }

  SessionPrincipalKind principalType() {
    return principalType;
  }

  UUID principalId() {
    return principalId;
  }

  String factorKind() {
    return factorKind;
  }

  boolean isUsable(Instant now) {
    return consumedAt == null && now.isBefore(expiresAt);
  }

  void consume(Instant now) {
    this.consumedAt = now;
  }
}
