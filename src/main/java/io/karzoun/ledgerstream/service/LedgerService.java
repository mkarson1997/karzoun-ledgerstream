package io.karzoun.ledgerstream.service;

import io.karzoun.ledgerstream.aggregate.Decision;
import io.karzoun.ledgerstream.aggregate.LedgerAggregate;
import io.karzoun.ledgerstream.command.PostEntryCommand;
import io.karzoun.ledgerstream.command.ReverseEntryCommand;
import io.karzoun.ledgerstream.event.LedgerEvent;
import io.karzoun.ledgerstream.policy.PostingPeriodPolicy;
import io.karzoun.ledgerstream.store.EventStore;
import io.karzoun.ledgerstream.store.EventStream;
import io.karzoun.ledgerstream.store.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LedgerService {
    private final EventStore eventStore;
    private final PostingPeriodPolicy periodPolicy;

    public LedgerService(EventStore eventStore, PostingPeriodPolicy periodPolicy) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.periodPolicy = Objects.requireNonNull(periodPolicy, "periodPolicy");
    }

    public PostingResult post(PostEntryCommand command) {
        return execute(command.ledgerId(), aggregate -> aggregate.decide(command, periodPolicy));
    }

    public PostingResult reverse(ReverseEntryCommand command) {
        return execute(command.ledgerId(), aggregate -> aggregate.decide(command, periodPolicy));
    }

    public LedgerAggregate load(String ledgerId) {
        EventStream stream = eventStore.load(ledgerId);
        return LedgerAggregate.replay(ledgerId, stream.events());
    }

    private PostingResult execute(String ledgerId, DecisionFunction decisionFunction) {
        EventStream stream = eventStore.load(ledgerId);
        LedgerAggregate aggregate = LedgerAggregate.replay(ledgerId, stream.events());
        Decision decision = decisionFunction.decide(aggregate);
        if (decision.events().isEmpty()) {
            return new PostingResult(decision.entryId(), stream.version(), true);
        }

        List<OutboxMessage> outbox = new ArrayList<>(decision.events().size());
        for (LedgerEvent event : decision.events()) {
            UUID messageId = UUID.nameUUIDFromBytes(("outbox\0" + event.eventId()).getBytes(StandardCharsets.UTF_8));
            outbox.add(new OutboxMessage(messageId, ledgerId, event.getClass().getSimpleName(),
                    event.recordedAt(), event.eventId()));
        }
        eventStore.appendAtomically(ledgerId, stream.version(), decision.events(), outbox);
        return new PostingResult(decision.entryId(), stream.version() + decision.events().size(), false);
    }

    @FunctionalInterface
    private interface DecisionFunction {
        Decision decide(LedgerAggregate aggregate);
    }
}
