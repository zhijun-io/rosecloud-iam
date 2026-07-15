package io.rosecloud.iam.operator;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operator_setup_token")
class OperatorSetupToken {

  @Id private UUID id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "begun_at")
  private Instant begunAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OperatorSetupToken() {}

  OperatorSetupToken(String tokenHash, Instant expiresAt) {
    this.id = UuidV7.next();
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  boolean hasBegun() {
    return begunAt != null;
  }

  boolean isCompleted() {
    return completedAt != null;
  }

  String tokenHash() {
    return tokenHash;
  }

  void markBegun() {
    this.begunAt = Instant.now();
  }

  void markCompleted() {
    this.completedAt = Instant.now();
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
