package io.rosecloud.iam.delivery;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxAppendService {

  private final OutboxMessageRepository outboxMessageRepository;

  public OutboxAppendService(OutboxMessageRepository outboxMessageRepository) {
    this.outboxMessageRepository = outboxMessageRepository;
  }

  public void append(String aggregateType, UUID aggregateId, String eventType, String payload) {
    outboxMessageRepository.save(new OutboxMessage(aggregateType, aggregateId, eventType, payload));
  }
}
