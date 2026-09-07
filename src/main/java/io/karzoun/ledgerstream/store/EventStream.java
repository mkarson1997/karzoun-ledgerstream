package io.karzoun.ledgerstream.store;

import io.karzoun.ledgerstream.event.LedgerEvent;
import java.util.List;

public record EventStream(long version, List<LedgerEvent> events) {
    public EventStream {
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        events = List.copyOf(events);
        if (version != events.size()) {
            throw new IllegalArgumentException("version must equal event count for the v0.1 stream contract");
        }
    }
}
