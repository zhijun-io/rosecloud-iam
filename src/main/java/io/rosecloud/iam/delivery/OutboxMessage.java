package io.rosecloud.iam.delivery;

import io.rosecloud.iam.shared.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 128)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxMessage() {}

  public OutboxMessage(String aggregateType, UUID aggregateId, String eventType, String payload) {
    this.id = UuidV7.next();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
  }

  public UUID id() {
    return id;
  }

  public String eventType() {
    return eventType;
  }

  public String payload() {
    return payload;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public void markPublished(Instant at) {
    this.publishedAt = at;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}
