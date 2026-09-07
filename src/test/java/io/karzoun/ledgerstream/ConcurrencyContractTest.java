package io.karzoun.ledgerstream;

import io.karzoun.ledgerstream.command.PostEntryCommand;
import io.karzoun.ledgerstream.domain.CurrencyCode;
import io.karzoun.ledgerstream.domain.Posting;
import io.karzoun.ledgerstream.domain.Side;
import io.karzoun.ledgerstream.event.LedgerEvent;
import io.karzoun.ledgerstream.policy.PostingPeriodPolicy;
import io.karzoun.ledgerstream.service.LedgerService;
import io.karzoun.ledgerstream.store.ConcurrencyException;
import io.karzoun.ledgerstream.store.EventStore;
import io.karzoun.ledgerstream.store.EventStream;
import io.karzoun.ledgerstream.store.InMemoryEventStore;
import io.karzoun.ledgerstream.store.OutboxMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConcurrencyContractTest {
    @Test
    void concurrentWritersCannotBothCommitTheSameExpectedVersion() throws Exception {
        InMemoryEventStore delegate = new InMemoryEventStore();
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        EventStore gated = new GatedLoadStore(delegate, bothLoaded, release);
        LedgerService service = new LedgerService(gated, PostingPeriodPolicy.alwaysOpen());

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = pool.submit(() -> service.post(command("a")));
            Future<?> second = pool.submit(() -> service.post(command("b")));
            bothLoaded.await();
            release.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<?> future : List.of(first, second)) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof ConcurrencyException) {
                        conflicts++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1L, delegate.load("ledger-concurrent").version());
            assertEquals(1, delegate.outboxSnapshot().size());
        }
    }

    private static PostEntryCommand command(String key) {
        CurrencyCode usd = new CurrencyCode("USD");
        return new PostEntryCommand("ledger-concurrent", key, Instant.parse("2026-01-01T00:00:00Z"),
                "concurrent", List.of(
                new Posting("cash", Side.DEBIT, usd, BigDecimal.ONE),
                new Posting("equity", Side.CREDIT, usd, BigDecimal.ONE)));
    }

    private static final class GatedLoadStore implements EventStore {
        private final EventStore delegate;
        private final CountDownLatch loaded;
        private final CountDownLatch release;

        private GatedLoadStore(EventStore delegate, CountDownLatch loaded, CountDownLatch release) {
            this.delegate = delegate;
            this.loaded = loaded;
            this.release = release;
        }

        @Override
        public EventStream load(String ledgerId) {
            EventStream snapshot = delegate.load(ledgerId);
            loaded.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
            return snapshot;
        }

        @Override
        public void appendAtomically(String ledgerId, long expectedVersion,
                                     List<? extends LedgerEvent> events,
                                     List<OutboxMessage> outboxMessages) {
            delegate.appendAtomically(ledgerId, expectedVersion, events, outboxMessages);
        }
    }
}
