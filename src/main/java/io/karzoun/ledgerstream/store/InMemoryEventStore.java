package io.karzoun.ledgerstream.store;

import io.karzoun.ledgerstream.event.LedgerEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryEventStore implements EventStore {
    private final Map<String, List<LedgerEvent>> streams = new HashMap<>();
    private final List<OutboxMessage> outbox = new ArrayList<>();

    @Override
    public synchronized EventStream load(String ledgerId) {
        List<LedgerEvent> events = streams.getOrDefault(ledgerId, List.of());
        return new EventStream(events.size(), events);
    }

    @Override
    public synchronized void appendAtomically(String ledgerId, long expectedVersion,
                                              List<? extends LedgerEvent> events,
                                              List<OutboxMessage> outboxMessages) {
        List<LedgerEvent> current = streams.computeIfAbsent(ledgerId, ignored -> new ArrayList<>());
        if (current.size() != expectedVersion) {
            throw new ConcurrencyException(ledgerId, expectedVersion, current.size());
        }
        if (events.size() != outboxMessages.size()) {
            throw new IllegalArgumentException("each appended event must have exactly one outbox message");
        }
        current.addAll(List.copyOf(events));
        outbox.addAll(List.copyOf(outboxMessages));
    }

    public synchronized List<OutboxMessage> outboxSnapshot() {
        return List.copyOf(outbox);
    }
}
