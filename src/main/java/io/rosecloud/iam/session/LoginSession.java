package io.rosecloud.iam.session;

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
@Table(name = "login_session")
class LoginSession {

  @Id private UUID id;

  @Column(name = "operator_id", nullable = false)
  private UUID operatorId;

  @Column(name = "refresh_token_hash", nullable = false, length = 64)
  private String refreshTokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LoginSession() {}

  LoginSession(UUID operatorId, String refreshTokenHash, Instant expiresAt) {
    this.id = UuidV7.next();
    this.operatorId = operatorId;
    this.refreshTokenHash = refreshTokenHash;
    this.expiresAt = expiresAt;
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
