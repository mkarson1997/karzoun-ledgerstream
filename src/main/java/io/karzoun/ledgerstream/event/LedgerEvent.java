package io.karzoun.ledgerstream.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface LedgerEvent permits JournalEntryPosted {
    UUID eventId();
    String ledgerId();
    Instant recordedAt();
}
