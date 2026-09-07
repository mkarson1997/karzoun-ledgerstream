package io.karzoun.ledgerstream.postgres;

import io.karzoun.ledgerstream.domain.CurrencyCode;
import io.karzoun.ledgerstream.domain.JournalEntry;
import io.karzoun.ledgerstream.domain.Posting;
import io.karzoun.ledgerstream.domain.Side;
import io.karzoun.ledgerstream.event.JournalEntryPosted;
import io.karzoun.ledgerstream.event.LedgerEvent;
import io.karzoun.ledgerstream.store.ConcurrencyException;
import io.karzoun.ledgerstream.store.EventStore;
import io.karzoun.ledgerstream.store.EventStream;
import io.karzoun.ledgerstream.store.OutboxMessage;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PostgresEventStore implements EventStore {
    private static final String LOAD_SQL = """
            SELECT e.stream_version, e.event_id, e.recorded_at, e.entry_id, e.idempotency_key,
                   e.booked_at, e.description, e.reversal_of, e.request_fingerprint,
                   p.ordinal, p.account_id, p.side, p.currency, p.amount
            FROM ledger_events e
            JOIN ledger_postings p
              ON p.ledger_id = e.ledger_id AND p.stream_version = e.stream_version
            WHERE e.ledger_id = ?
            ORDER BY e.stream_version, p.ordinal
            """;

    private final DataSource dataSource;

    public PostgresEventStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public EventStream load(String ledgerId) {
        Objects.requireNonNull(ledgerId, "ledgerId");
        try (Connection connection = dataSource.getConnection()) {
            long version = loadVersion(connection, ledgerId);
            List<LedgerEvent> events = loadEvents(connection, ledgerId);
            if (version != events.size()) {
                throw new PersistenceException(
                        "stream version/event count mismatch for ledger " + ledgerId,
                        new IllegalStateException("version=" + version + ", events=" + events.size()));
            }
            return new EventStream(version, events);
        } catch (SQLException exception) {
            throw new PersistenceException("failed to load ledger " + ledgerId, exception);
        }
    }

    @Override
    public void appendAtomically(String ledgerId, long expectedVersion,
                                 List<? extends LedgerEvent> events,
                                 List<OutboxMessage> outboxMessages) {
        Objects.requireNonNull(ledgerId, "ledgerId");
        List<? extends LedgerEvent> eventCopy = List.copyOf(events);
        List<OutboxMessage> outboxCopy = List.copyOf(outboxMessages);
        if (eventCopy.isEmpty()) {
            return;
        }
        if (eventCopy.size() != outboxCopy.size()) {
            throw new IllegalArgumentException("each appended event must have exactly one outbox message");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        validateBatch(ledgerId, eventCopy, outboxCopy);

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureStream(connection, ledgerId);
                long newVersion = expectedVersion + eventCopy.size();
                int updated = advanceVersion(connection, ledgerId, expectedVersion, newVersion);
                if (updated != 1) {
                    long actual = loadVersion(connection, ledgerId);
                    throw new ConcurrencyException(ledgerId, expectedVersion, actual);
                }
                for (int index = 0; index < eventCopy.size(); index++) {
                    long streamVersion = expectedVersion + index + 1;
                    insertEvent(connection, streamVersion, (JournalEntryPosted) eventCopy.get(index));
                    insertOutbox(connection, streamVersion, outboxCopy.get(index));
                }
                connection.commit();
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (ConcurrencyException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("failed to append ledger " + ledgerId, exception);
        }
    }

    private static void validateBatch(String ledgerId, List<? extends LedgerEvent> events,
                                      List<OutboxMessage> outboxMessages) {
        for (int index = 0; index < events.size(); index++) {
            LedgerEvent event = events.get(index);
            OutboxMessage outbox = outboxMessages.get(index);
            if (!(event instanceof JournalEntryPosted)) {
                throw new IllegalArgumentException("unsupported event type: " + event.getClass().getName());
            }
            if (!ledgerId.equals(event.ledgerId()) || !ledgerId.equals(outbox.aggregateId())) {
                throw new IllegalArgumentException("event/outbox aggregate does not match ledgerId");
            }
            if (!event.eventId().equals(outbox.eventId())) {
                throw new IllegalArgumentException("outbox eventId does not match ledger eventId");
            }
        }
    }

    private static void ensureStream(Connection connection, String ledgerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ledger_streams(ledger_id, version) VALUES (?, 0) ON CONFLICT (ledger_id) DO NOTHING")) {
            statement.setString(1, ledgerId);
            statement.executeUpdate();
        }
    }

    private static int advanceVersion(Connection connection, String ledgerId, long expected, long next)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ledger_streams SET version = ?, updated_at = now() WHERE ledger_id = ? AND version = ?")) {
            statement.setLong(1, next);
            statement.setString(2, ledgerId);
            statement.setLong(3, expected);
            return statement.executeUpdate();
        }
    }

    private static long loadVersion(Connection connection, String ledgerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM ledger_streams WHERE ledger_id = ?")) {
            statement.setString(1, ledgerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private static List<LedgerEvent> loadEvents(Connection connection, String ledgerId) throws SQLException {
        List<LedgerEvent> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_SQL)) {
            statement.setString(1, ledgerId);
            try (ResultSet result = statement.executeQuery()) {
                long currentVersion = -1;
                EventBuilder builder = null;
                while (result.next()) {
                    long streamVersion = result.getLong("stream_version");
                    if (streamVersion != currentVersion) {
                        if (builder != null) {
                            events.add(builder.build(ledgerId));
                        }
                        builder = EventBuilder.from(result);
                        currentVersion = streamVersion;
                    }
                    builder.addPosting(result);
                }
                if (builder != null) {
                    events.add(builder.build(ledgerId));
                }
            }
        }
        return events;
    }

    private static void insertEvent(Connection connection, long streamVersion, JournalEntryPosted event)
            throws SQLException {
        JournalEntry entry = event.entry();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_events(
                    ledger_id, stream_version, event_id, event_type, recorded_at,
                    entry_id, idempotency_key, booked_at, description, reversal_of, request_fingerprint)
                VALUES (?, ?, ?, 'JournalEntryPosted', ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, event.ledgerId());
            statement.setLong(2, streamVersion);
            statement.setObject(3, event.eventId());
            statement.setTimestamp(4, Timestamp.from(event.recordedAt()));
            statement.setObject(5, entry.entryId());
            statement.setString(6, entry.idempotencyKey());
            statement.setTimestamp(7, Timestamp.from(entry.bookedAt()));
            statement.setString(8, entry.description());
            statement.setObject(9, entry.reversalOf());
            statement.setString(10, event.requestFingerprint());
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_postings(
                    ledger_id, stream_version, ordinal, account_id, side, currency, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            int ordinal = 0;
            for (Posting posting : entry.postings()) {
                statement.setString(1, event.ledgerId());
                statement.setLong(2, streamVersion);
                statement.setInt(3, ordinal++);
                statement.setString(4, posting.accountId());
                statement.setString(5, posting.side().name());
                statement.setString(6, posting.currency().value());
                statement.setBigDecimal(7, posting.amount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertOutbox(Connection connection, long streamVersion, OutboxMessage message)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_outbox(
                    message_id, ledger_id, stream_version, event_id, event_type, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, message.messageId());
            statement.setString(2, message.aggregateId());
            statement.setLong(3, streamVersion);
            statement.setObject(4, message.eventId());
            statement.setString(5, message.eventType());
            statement.setTimestamp(6, Timestamp.from(message.occurredAt()));
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static final class EventBuilder {
        private final UUID eventId;
        private final Instant recordedAt;
        private final UUID entryId;
        private final String idempotencyKey;
        private final Instant bookedAt;
        private final String description;
        private final UUID reversalOf;
        private final String fingerprint;
        private final List<Posting> postings = new ArrayList<>();

        private EventBuilder(UUID eventId, Instant recordedAt, UUID entryId, String idempotencyKey,
                             Instant bookedAt, String description, UUID reversalOf, String fingerprint) {
            this.eventId = eventId;
            this.recordedAt = recordedAt;
            this.entryId = entryId;
            this.idempotencyKey = idempotencyKey;
            this.bookedAt = bookedAt;
            this.description = description;
            this.reversalOf = reversalOf;
            this.fingerprint = fingerprint;
        }

        static EventBuilder from(ResultSet result) throws SQLException {
            return new EventBuilder(result.getObject("event_id", UUID.class),
                    result.getTimestamp("recorded_at").toInstant(), result.getObject("entry_id", UUID.class),
                    result.getString("idempotency_key"), result.getTimestamp("booked_at").toInstant(),
                    result.getString("description"), result.getObject("reversal_of", UUID.class),
                    result.getString("request_fingerprint"));
        }

        void addPosting(ResultSet result) throws SQLException {
            postings.add(new Posting(result.getString("account_id"), Side.valueOf(result.getString("side")),
                    new CurrencyCode(result.getString("currency")), result.getBigDecimal("amount")));
        }

        LedgerEvent build(String ledgerId) {
            JournalEntry entry = new JournalEntry(entryId, idempotencyKey, bookedAt, description, postings, reversalOf);
            return new JournalEntryPosted(eventId, ledgerId, recordedAt, entry, fingerprint);
        }
    }
}
