package io.karzoun.ledgerstream.postgres;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimedOutboxMessage(UUID messageId, String ledgerId, long streamVersion, UUID eventId,
                                   String eventType, Instant occurredAt, int attempts,
                                   String lockedBy, Instant lockedUntil) {
    public ClaimedOutboxMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(lockedBy, "lockedBy");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        if (streamVersion < 1 || attempts < 1) {
            throw new IllegalArgumentException("streamVersion and attempts must be positive");
        }
    }
}
