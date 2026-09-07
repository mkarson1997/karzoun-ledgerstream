package io.karzoun.ledgerstream;

import io.karzoun.ledgerstream.aggregate.AlreadyReversedException;
import io.karzoun.ledgerstream.aggregate.IdempotencyConflictException;
import io.karzoun.ledgerstream.aggregate.LedgerAggregate;
import io.karzoun.ledgerstream.command.PostEntryCommand;
import io.karzoun.ledgerstream.command.ReverseEntryCommand;
import io.karzoun.ledgerstream.domain.CurrencyCode;
import io.karzoun.ledgerstream.domain.JournalEntry;
import io.karzoun.ledgerstream.domain.Posting;
import io.karzoun.ledgerstream.domain.Side;
import io.karzoun.ledgerstream.domain.UnbalancedEntryException;
import io.karzoun.ledgerstream.event.JournalEntryPosted;
import io.karzoun.ledgerstream.policy.ClosedBeforePolicy;
import io.karzoun.ledgerstream.policy.ClosedPeriodException;
import io.karzoun.ledgerstream.policy.PostingPeriodPolicy;
import io.karzoun.ledgerstream.service.LedgerService;
import io.karzoun.ledgerstream.service.PostingResult;
import io.karzoun.ledgerstream.store.ConcurrencyException;
import io.karzoun.ledgerstream.store.InMemoryEventStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LedgerCoreTest {
    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsUnbalancedEntryPerCurrency() {
        List<Posting> postings = List.of(debit("cash", USD, "10.00"), credit("revenue", USD, "9.99"));
        assertThrows(UnbalancedEntryException.class, () -> new JournalEntry(
                UUID.randomUUID(), "k1", T0, "bad", postings, null));
    }

    @Test
    void requiresEveryCurrencyToBalanceIndependently() {
        List<Posting> postings = List.of(
                debit("cash-usd", USD, "10"), credit("revenue-usd", USD, "10"),
                debit("cash-eur", EUR, "8"), credit("revenue-eur", EUR, "8"));
        JournalEntry entry = new JournalEntry(UUID.randomUUID(), "k2", T0, "fx legs", postings, null);
        assertEquals(4, entry.postings().size());
    }

    @Test
    void sameIdempotencyKeyAndPayloadIsANoOpReplay() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        PostEntryCommand command = post("ledger-a", "pay-1", "25.50");
        PostingResult first = service.post(command);
        PostingResult second = service.post(command);
        assertFalse(first.idempotentReplay());
        assertTrue(second.idempotentReplay());
        assertEquals(first.entryId(), second.entryId());
        assertEquals(1L, second.streamVersion());
        assertEquals(1, store.outboxSnapshot().size());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        service.post(post("ledger-a", "pay-2", "25.50"));
        assertThrows(IdempotencyConflictException.class,
                () -> service.post(post("ledger-a", "pay-2", "26.00")));
        assertEquals(1, service.load("ledger-a").entries().size());
    }

    @Test
    void reversalAppendsOppositeEntryAndPreservesHistory() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        PostingResult original = service.post(post("ledger-r", "sale-1", "40"));
        PostingResult reversal = service.reverse(new ReverseEntryCommand(
                "ledger-r", original.entryId(), "reverse-sale-1", T0.plusSeconds(60), "customer refund"));
        LedgerAggregate aggregate = service.load("ledger-r");
        assertEquals(2, aggregate.entries().size());
        assertEquals(BigDecimal.ZERO, aggregate.accountBalance("cash", USD));
        assertEquals(BigDecimal.ZERO, aggregate.accountBalance("revenue", USD));
        assertFalse(reversal.idempotentReplay());
        assertEquals(original.entryId(), aggregate.entries().get(1).reversalOf());
    }

    @Test
    void originalEntryCannotBeReversedTwiceWithDifferentCommands() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        PostingResult original = service.post(post("ledger-r2", "sale-1", "40"));
        service.reverse(new ReverseEntryCommand("ledger-r2", original.entryId(), "reverse-1",
                T0.plusSeconds(1), "void"));
        assertThrows(AlreadyReversedException.class, () -> service.reverse(new ReverseEntryCommand(
                "ledger-r2", original.entryId(), "reverse-2", T0.plusSeconds(2), "void again")));
    }

    @Test
    void staleExpectedVersionIsRejectedAtomically() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        service.post(post("ledger-c", "one", "10"));
        JournalEntry entry = new JournalEntry(UUID.randomUUID(), "manual", T0, "manual",
                List.of(debit("cash", USD, "1"), credit("revenue", USD, "1")), null);
        JournalEntryPosted event = new JournalEntryPosted(UUID.randomUUID(), "ledger-c", T0, entry, "fingerprint");
        assertThrows(ConcurrencyException.class,
                () -> store.appendAtomically("ledger-c", 0, List.of(event), List.of()));
        assertEquals(1, store.load("ledger-c").events().size());
    }

    @Test
    void replayReconstructsTheSameBalanceAndVersion() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        service.post(post("ledger-replay", "one", "15"));
        service.post(post("ledger-replay", "two", "4"));
        LedgerAggregate first = service.load("ledger-replay");
        LedgerAggregate second = LedgerAggregate.replay("ledger-replay", store.load("ledger-replay").events());
        assertEquals(first.version(), second.version());
        assertEquals(first.accountBalance("cash", USD), second.accountBalance("cash", USD));
        assertEquals(new BigDecimal("19"), second.accountBalance("cash", USD));
    }

    @Test
    void closedAccountingPeriodRejectsPostingBeforeAppend() {
        InMemoryEventStore store = new InMemoryEventStore();
        LedgerService service = new LedgerService(store, new ClosedBeforePolicy(T0.plusSeconds(10)));
        assertThrows(ClosedPeriodException.class, () -> service.post(post("ledger-p", "closed", "5")));
        assertEquals(0L, store.load("ledger-p").version());
        assertTrue(store.outboxSnapshot().isEmpty());
    }

    private static PostEntryCommand post(String ledger, String key, String amount) {
        return new PostEntryCommand(ledger, key, T0, "sale",
                List.of(debit("cash", USD, amount), credit("revenue", USD, amount)));
    }

    private static Posting debit(String account, CurrencyCode currency, String amount) {
        return new Posting(account, Side.DEBIT, currency, new BigDecimal(amount));
    }

    private static Posting credit(String account, CurrencyCode currency, String amount) {
        return new Posting(account, Side.CREDIT, currency, new BigDecimal(amount));
    }
}
