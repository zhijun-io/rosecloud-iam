package io.rosecloud.iam.tenancy;

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
@Table(name = "membership")
public class Membership {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_code", nullable = false, length = 64)
  private String roleCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private MembershipStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Membership() {}

  public Membership(UUID tenantId, UUID userId, String roleCode, MembershipStatus status) {
    this.id = UuidV7.next();
    this.tenantId = tenantId;
    this.userId = userId;
    this.roleCode = roleCode;
    this.status = status;
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
