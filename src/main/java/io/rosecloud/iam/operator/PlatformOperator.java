package io.rosecloud.iam.operator;

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
@Table(name = "platform_operator")
class PlatformOperator {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OperatorStatus status;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "totp_secret_ciphertext")
  private String totpSecretCiphertext;

  @Column(name = "totp_secret_key_id", length = 64)
  private String totpSecretKeyId;

  @Column(name = "pending_totp_secret_ciphertext")
  private String pendingTotpSecretCiphertext;

  @Column(name = "pending_totp_secret_key_id", length = 64)
  private String pendingTotpSecretKeyId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlatformOperator() {}

  PlatformOperator(
      String passwordHash, String totpSecretCiphertext, String totpSecretKeyId, OperatorStatus status) {
    this.id = UuidV7.next();
    this.passwordHash = passwordHash;
    this.totpSecretCiphertext = totpSecretCiphertext;
    this.totpSecretKeyId = totpSecretKeyId;
    this.status = status;
  }

  UUID id() {
    return id;
  }

  OperatorStatus status() {
    return status;
  }

  String passwordHash() {
    return passwordHash;
  }

  String totpSecretCiphertext() {
    return totpSecretCiphertext;
  }

  String totpSecretKeyId() {
    return totpSecretKeyId;
  }

  boolean hasTotpBinding() {
    return totpSecretCiphertext != null
        && !totpSecretCiphertext.isBlank()
        && totpSecretKeyId != null
        && !totpSecretKeyId.isBlank();
  }

  void bindTotp(String totpSecretCiphertext, String totpSecretKeyId) {
    this.totpSecretCiphertext = totpSecretCiphertext;
    this.totpSecretKeyId = totpSecretKeyId;
    clearPendingTotp();
  }

  void beginPendingTotp(String ciphertext, String keyId) {
    this.pendingTotpSecretCiphertext = ciphertext;
    this.pendingTotpSecretKeyId = keyId;
  }

  String pendingTotpSecretCiphertext() {
    return pendingTotpSecretCiphertext;
  }

  String pendingTotpSecretKeyId() {
    return pendingTotpSecretKeyId;
  }

  boolean hasPendingTotp() {
    return pendingTotpSecretCiphertext != null
        && !pendingTotpSecretCiphertext.isBlank()
        && pendingTotpSecretKeyId != null
        && !pendingTotpSecretKeyId.isBlank();
  }

  void clearPendingTotp() {
    this.pendingTotpSecretCiphertext = null;
    this.pendingTotpSecretKeyId = null;
  }

  void clearTotp() {
    this.totpSecretCiphertext = null;
    this.totpSecretKeyId = null;
    clearPendingTotp();
  }

  void activate() {
    this.status = OperatorStatus.ACTIVE;
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
