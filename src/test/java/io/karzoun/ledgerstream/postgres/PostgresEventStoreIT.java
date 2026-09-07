package io.karzoun.ledgerstream.postgres;

import io.karzoun.ledgerstream.command.PostEntryCommand;
import io.karzoun.ledgerstream.domain.CurrencyCode;
import io.karzoun.ledgerstream.domain.JournalEntry;
import io.karzoun.ledgerstream.domain.Posting;
import io.karzoun.ledgerstream.domain.Side;
import io.karzoun.ledgerstream.event.JournalEntryPosted;
import io.karzoun.ledgerstream.policy.PostingPeriodPolicy;
import io.karzoun.ledgerstream.service.LedgerService;
import io.karzoun.ledgerstream.store.OutboxMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
final class PostgresEventStoreIT {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ledgerstream")
            .withUsername("ledgerstream")
            .withPassword("ledgerstream");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        PostgresLedgerSchema.migrate(dataSource);
    }

    @BeforeEach
    void clearDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "TRUNCATE ledger_outbox, ledger_postings, ledger_events, ledger_streams")) {
            statement.execute();
        }
    }

    @Test
    void persistsAndReconstructsAcrossFreshStoreInstances() {
        LedgerService writer = new LedgerService(new PostgresEventStore(dataSource), PostingPeriodPolicy.alwaysOpen());
        writer.post(post("ledger-restart", "one", "12.50"));
        writer.post(post("ledger-restart", "two", "7.50"));
        LedgerService afterRestart = new LedgerService(new PostgresEventStore(dataSource), PostingPeriodPolicy.alwaysOpen());
        assertEquals(2L, afterRestart.load("ledger-restart").version());
        assertEquals(new BigDecimal("2E+1"), afterRestart.load("ledger-restart").accountBalance("cash", USD));
        var replay = afterRestart.post(post("ledger-restart", "one", "12.50"));
        assertTrue(replay.idempotentReplay());
        assertEquals(2L, replay.streamVersion());
        assertEquals(2L, count("ledger_events"));
        assertEquals(2L, count("ledger_outbox"));
    }

    @Test
    void databaseFailureRollsBackVersionEventAndPostings() throws Exception {
        PostgresEventStore store = new PostgresEventStore(dataSource);
        LedgerService service = new LedgerService(store, PostingPeriodPolicy.alwaysOpen());
        service.post(post("ledger-rollback", "one", "10"));
        UUID existingMessageId;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT message_id FROM ledger_outbox WHERE ledger_id = ?")) {
            statement.setString(1, "ledger-rollback");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                existingMessageId = result.getObject(1, UUID.class);
            }
        }

        UUID entryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        JournalEntry entry = new JournalEntry(entryId, "two", T0.plusSeconds(1), "second",
                List.of(debit("cash", "3"), credit("revenue", "3")), null);
        JournalEntryPosted event = new JournalEntryPosted(eventId, "ledger-rollback", T0.plusSeconds(1),
                entry, "a".repeat(64));
        OutboxMessage duplicatePrimaryKey = new OutboxMessage(existingMessageId, "ledger-rollback",
                "JournalEntryPosted", T0.plusSeconds(1), eventId);

        assertThrows(PersistenceException.class,
                () -> store.appendAtomically("ledger-rollback", 1, List.of(event), List.of(duplicatePrimaryKey)));
        assertEquals(1L, store.load("ledger-rollback").version());
        assertEquals(1L, count("ledger_events"));
        assertEquals(2L, count("ledger_postings"));
        assertEquals(1L, count("ledger_outbox"));
    }

    @Test
    void outboxWorkersClaimDisjointBatchesWithLeases() {
        LedgerService service = new LedgerService(new PostgresEventStore(dataSource), PostingPeriodPolicy.alwaysOpen());
        service.post(post("ledger-outbox", "one", "1"));
        service.post(post("ledger-outbox", "two", "2"));
        service.post(post("ledger-outbox", "three", "3"));
        PostgresOutboxRepository outbox = new PostgresOutboxRepository(dataSource);
        List<ClaimedOutboxMessage> first = outbox.claimBatch("worker-a", T0.plusSeconds(10), Duration.ofMinutes(1), 2);
        List<ClaimedOutboxMessage> second = outbox.claimBatch("worker-b", T0.plusSeconds(10), Duration.ofMinutes(1), 2);
        assertEquals(2, first.size());
        assertEquals(1, second.size());
        Set<UUID> ids = new HashSet<>();
        first.forEach(message -> ids.add(message.messageId()));
        second.forEach(message -> ids.add(message.messageId()));
        assertEquals(3, ids.size());
        assertTrue(first.stream().allMatch(message -> message.attempts() == 1));
        assertTrue(second.stream().allMatch(message -> message.attempts() == 1));
        UUID messageId = first.getFirst().messageId();
        assertThrows(OutboxLeaseLostException.class,
                () -> outbox.markPublished(messageId, "worker-b", T0.plusSeconds(20)));
        outbox.markPublished(messageId, "worker-a", T0.plusSeconds(20));
    }

    private static PostEntryCommand post(String ledger, String key, String amount) {
        return new PostEntryCommand(ledger, key, T0, "sale",
                List.of(debit("cash", amount), credit("revenue", amount)));
    }

    private static Posting debit(String account, String amount) {
        return new Posting(account, Side.DEBIT, USD, new BigDecimal(amount));
    }

    private static Posting credit(String account, String amount) {
        return new Posting(account, Side.CREDIT, USD, new BigDecimal(amount));
    }

    private static long count(String table) {
        Set<String> allowed = Set.of("ledger_events", "ledger_postings", "ledger_outbox");
        if (!allowed.contains(table)) {
            throw new IllegalArgumentException("unexpected table");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
