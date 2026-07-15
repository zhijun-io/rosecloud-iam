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
@Table(name = "tenant")
public class Tenant {

  @Id private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private TenantStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Tenant() {}

  public Tenant(String name, TenantStatus status) {
    this.id = UuidV7.next();
    this.name = name;
    this.status = status;
  }

  public UUID id() {
    return id;
  }

  public TenantStatus status() {
    return status;
  }

  public void activate() {
    this.status = TenantStatus.ACTIVE;
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
