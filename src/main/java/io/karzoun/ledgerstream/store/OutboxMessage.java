package io.karzoun.ledgerstream.store;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxMessage(UUID messageId, String aggregateId, String eventType,
                            Instant occurredAt, UUID eventId) {
    public OutboxMessage {
        Objects.requireNonNull(messageId, "messageId");
        aggregateId = Objects.requireNonNull(aggregateId, "aggregateId").trim();
        eventType = Objects.requireNonNull(eventType, "eventType").trim();
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(eventId, "eventId");
        if (aggregateId.isEmpty() || eventType.isEmpty()) {
            throw new IllegalArgumentException("aggregateId and eventType must not be blank");
        }
    }
}
