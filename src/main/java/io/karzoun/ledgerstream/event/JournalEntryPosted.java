package io.karzoun.ledgerstream.event;

import io.karzoun.ledgerstream.domain.JournalEntry;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record JournalEntryPosted(UUID eventId, String ledgerId, Instant recordedAt,
                                 JournalEntry entry, String requestFingerprint) implements LedgerEvent {
    public JournalEntryPosted {
        Objects.requireNonNull(eventId, "eventId");
        ledgerId = Objects.requireNonNull(ledgerId, "ledgerId").trim();
        if (ledgerId.isEmpty()) {
            throw new IllegalArgumentException("ledgerId must not be blank");
        }
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(entry, "entry");
        requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint");
    }
}
