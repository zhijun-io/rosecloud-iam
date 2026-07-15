package io.rosecloud.iam.tenancy;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation")
public class Invitation {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "role_code", nullable = false, length = 64)
  private String roleCode;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Invitation() {}

  public Invitation(UUID tenantId, String email, String roleCode, String tokenHash, Instant expiresAt) {
    this.id = UuidV7.next();
    this.tenantId = tenantId;
    this.email = email;
    this.roleCode = roleCode;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public String email() {
    return email;
  }

  public String roleCode() {
    return roleCode;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public boolean isAccepted() {
    return acceptedAt != null;
  }

  public void markAccepted() {
    this.acceptedAt = Instant.now();
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}
