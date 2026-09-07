package io.karzoun.ledgerstream.store;

import io.karzoun.ledgerstream.event.LedgerEvent;
import java.util.List;

public interface EventStore {
    EventStream load(String ledgerId);

    void appendAtomically(String ledgerId, long expectedVersion, List<? extends LedgerEvent> events,
                          List<OutboxMessage> outboxMessages);
}
