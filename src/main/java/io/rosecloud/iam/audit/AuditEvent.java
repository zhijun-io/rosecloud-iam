package io.rosecloud.iam.audit;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
class AuditEvent {

  @Id private UUID id;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "details")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditEvent() {}

  AuditEvent(String eventType, UUID actorId, String details) {
    this.id = UuidV7.next();
    this.eventType = eventType;
    this.actorId = actorId;
    this.details = details;
    this.createdAt = Instant.now();
  }
}
