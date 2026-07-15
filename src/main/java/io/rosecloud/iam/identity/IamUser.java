package io.rosecloud.iam.identity;

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
@Table(name = "iam_user")
public class IamUser {

  @Id private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private UserStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected IamUser() {}

  public IamUser(
      String email,
      String passwordHash,
      String totpSecretCiphertext,
      String totpSecretKeyId,
      UserStatus status) {
    this.id = UuidV7.next();
    this.email = email;
    this.passwordHash = passwordHash;
    this.totpSecretCiphertext = totpSecretCiphertext;
    this.totpSecretKeyId = totpSecretKeyId;
    this.status = status;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String totpSecretCiphertext() {
    return totpSecretCiphertext;
  }

  public String totpSecretKeyId() {
    return totpSecretKeyId;
  }

  public UserStatus status() {
    return status;
  }

  public boolean hasTotpBinding() {
    return totpSecretCiphertext != null
        && !totpSecretCiphertext.isBlank()
        && totpSecretKeyId != null
        && !totpSecretKeyId.isBlank();
  }

  public void replacePendingEnrollment(String passwordHash) {
    this.passwordHash = passwordHash;
    this.totpSecretCiphertext = null;
    this.totpSecretKeyId = null;
    this.status = UserStatus.PENDING_TOTP;
    this.emailVerifiedAt = null;
  }

  public void bindTotp(String totpSecretCiphertext, String totpSecretKeyId) {
    this.totpSecretCiphertext = totpSecretCiphertext;
    this.totpSecretKeyId = totpSecretKeyId;
    clearPendingTotp();
  }

  public void beginPendingTotp(String ciphertext, String keyId) {
    this.pendingTotpSecretCiphertext = ciphertext;
    this.pendingTotpSecretKeyId = keyId;
  }

  public String pendingTotpSecretCiphertext() {
    return pendingTotpSecretCiphertext;
  }

  public String pendingTotpSecretKeyId() {
    return pendingTotpSecretKeyId;
  }

  public boolean hasPendingTotp() {
    return pendingTotpSecretCiphertext != null
        && !pendingTotpSecretCiphertext.isBlank()
        && pendingTotpSecretKeyId != null
        && !pendingTotpSecretKeyId.isBlank();
  }

  public void clearPendingTotp() {
    this.pendingTotpSecretCiphertext = null;
    this.pendingTotpSecretKeyId = null;
  }

  public void clearTotp() {
    this.totpSecretCiphertext = null;
    this.totpSecretKeyId = null;
    clearPendingTotp();
  }

  public void activate() {
    this.status = UserStatus.ACTIVE;
    this.emailVerifiedAt = Instant.now();
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
