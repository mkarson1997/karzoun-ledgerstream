package io.karzoun.ledgerstream.postgres;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PostgresOutboxRepository {
    private final DataSource dataSource;

    public PostgresOutboxRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<ClaimedOutboxMessage> claimBatch(String workerId, Instant now, Duration lease, int limit) {
        workerId = requireWorker(workerId);
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("lease must be > 0 and <= 1 hour");
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be in 1..500");
        }
        Instant lockedUntil = now.plus(lease);
        String sql = """
                WITH candidates AS (
                    SELECT message_id
                    FROM ledger_outbox
                    WHERE published_at IS NULL
                      AND (locked_until IS NULL OR locked_until <= ?)
                    ORDER BY occurred_at, message_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE ledger_outbox o
                   SET locked_by = ?, locked_until = ?, attempts = attempts + 1
                  FROM candidates c
                 WHERE o.message_id = c.message_id
                RETURNING o.message_id, o.ledger_id, o.stream_version, o.event_id, o.event_type,
                          o.occurred_at, o.attempts, o.locked_by, o.locked_until
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, limit);
                statement.setString(3, workerId);
                statement.setTimestamp(4, Timestamp.from(lockedUntil));
                List<ClaimedOutboxMessage> claimed = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        claimed.add(readClaim(result));
                    }
                }
                connection.commit();
                return List.copyOf(claimed);
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("failed to claim outbox messages", exception);
        }
    }

    public void markPublished(UUID messageId, String workerId, Instant publishedAt) {
        Objects.requireNonNull(messageId, "messageId");
        workerId = requireWorker(workerId);
        Objects.requireNonNull(publishedAt, "publishedAt");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE ledger_outbox
                        SET published_at = ?, locked_by = NULL, locked_until = NULL
                      WHERE message_id = ? AND locked_by = ? AND published_at IS NULL
                     """)) {
            statement.setTimestamp(1, Timestamp.from(publishedAt));
            statement.setObject(2, messageId);
            statement.setString(3, workerId);
            if (statement.executeUpdate() != 1) {
                throw new OutboxLeaseLostException(messageId, workerId);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("failed to mark outbox message published", exception);
        }
    }

    public void release(UUID messageId, String workerId) {
        Objects.requireNonNull(messageId, "messageId");
        workerId = requireWorker(workerId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE ledger_outbox
                        SET locked_by = NULL, locked_until = NULL
                      WHERE message_id = ? AND locked_by = ? AND published_at IS NULL
                     """)) {
            statement.setObject(1, messageId);
            statement.setString(2, workerId);
            if (statement.executeUpdate() != 1) {
                throw new OutboxLeaseLostException(messageId, workerId);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("failed to release outbox lease", exception);
        }
    }

    private static ClaimedOutboxMessage readClaim(ResultSet result) throws SQLException {
        return new ClaimedOutboxMessage(result.getObject("message_id", UUID.class), result.getString("ledger_id"),
                result.getLong("stream_version"), result.getObject("event_id", UUID.class),
                result.getString("event_type"), result.getTimestamp("occurred_at").toInstant(),
                result.getInt("attempts"), result.getString("locked_by"),
                result.getTimestamp("locked_until").toInstant());
    }

    private static String requireWorker(String workerId) {
        Objects.requireNonNull(workerId, "workerId");
        workerId = workerId.trim();
        if (workerId.isEmpty() || workerId.length() > 128) {
            throw new IllegalArgumentException("workerId must contain 1..128 characters");
        }
        return workerId;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
