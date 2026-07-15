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

  @Column(name = "totp_secret_ciphertext", nullable = false)
  private String totpSecretCiphertext;

  @Column(name = "totp_secret_key_id", nullable = false, length = 64)
  private String totpSecretKeyId;

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

  public void replacePendingEnrollment(
      String passwordHash, String totpSecretCiphertext, String totpSecretKeyId) {
    this.passwordHash = passwordHash;
    this.totpSecretCiphertext = totpSecretCiphertext;
    this.totpSecretKeyId = totpSecretKeyId;
    this.status = UserStatus.PENDING_TOTP;
    this.emailVerifiedAt = null;
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
