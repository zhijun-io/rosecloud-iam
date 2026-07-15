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
@Table(name = "recovery_code")
class RecoveryCode {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "principal_type", nullable = false, length = 16)
  private SessionPrincipalKind principalType;

  @Column(name = "principal_id", nullable = false)
  private UUID principalId;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RecoveryCode() {}

  RecoveryCode(SessionPrincipalKind principalType, UUID principalId, String codeHash) {
    this.id = UuidV7.next();
    this.principalType = principalType;
    this.principalId = principalId;
    this.codeHash = codeHash;
    this.createdAt = Instant.now();
  }

  String codeHash() {
    return codeHash;
  }

  boolean used() {
    return usedAt != null;
  }

  void markUsed(Instant now) {
    this.usedAt = now;
  }
}
