package io.karzoun.ledgerstream.aggregate;

import io.karzoun.ledgerstream.event.LedgerEvent;
import java.util.List;
import java.util.UUID;

public record Decision(UUID entryId, List<LedgerEvent> events, boolean idempotentReplay) {
    public Decision {
        events = List.copyOf(events);
    }
}
