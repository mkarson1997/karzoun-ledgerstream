package io.karzoun.ledgerstream.aggregate;

import io.karzoun.ledgerstream.command.PostEntryCommand;
import io.karzoun.ledgerstream.command.ReverseEntryCommand;
import io.karzoun.ledgerstream.domain.CurrencyCode;
import io.karzoun.ledgerstream.domain.JournalEntry;
import io.karzoun.ledgerstream.domain.Posting;
import io.karzoun.ledgerstream.domain.Side;
import io.karzoun.ledgerstream.event.JournalEntryPosted;
import io.karzoun.ledgerstream.event.LedgerEvent;
import io.karzoun.ledgerstream.policy.PostingPeriodPolicy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LedgerAggregate {
    private final String ledgerId;
    private final Map<UUID, JournalEntry> entries = new LinkedHashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
    private final Set<UUID> reversedEntries = new HashSet<>();
    private long version;

    private LedgerAggregate(String ledgerId) {
        this.ledgerId = requireLedgerId(ledgerId);
    }

    public static LedgerAggregate empty(String ledgerId) {
        return new LedgerAggregate(ledgerId);
    }

    public static LedgerAggregate replay(String ledgerId, List<? extends LedgerEvent> events) {
        LedgerAggregate aggregate = new LedgerAggregate(ledgerId);
        for (LedgerEvent event : List.copyOf(events)) {
            aggregate.apply(event);
        }
        return aggregate;
    }

    public Decision decide(PostEntryCommand command, PostingPeriodPolicy periodPolicy) {
        requireLedger(command.ledgerId());
        Objects.requireNonNull(periodPolicy, "periodPolicy").requireOpen(command.bookedAt());
        String fingerprint = fingerprint(command);
        IdempotencyRecord previous = idempotency.get(command.idempotencyKey());
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return new Decision(previous.entryId(), List.of(), true);
        }

        UUID entryId = StableIds.uuid("ledger-entry:" + ledgerId, command.idempotencyKey());
        JournalEntry entry = new JournalEntry(entryId, command.idempotencyKey(), command.bookedAt(),
                command.description(), command.postings(), null);
        JournalEntryPosted event = new JournalEntryPosted(
                StableIds.uuid("ledger-event:" + ledgerId, entryId.toString()), ledgerId,
                command.bookedAt(), entry, fingerprint);
        return new Decision(entryId, List.of(event), false);
    }

    public Decision decide(ReverseEntryCommand command, PostingPeriodPolicy periodPolicy) {
        requireLedger(command.ledgerId());
        Objects.requireNonNull(periodPolicy, "periodPolicy").requireOpen(command.bookedAt());
        String fingerprint = fingerprint(command);
        IdempotencyRecord previous = idempotency.get(command.idempotencyKey());
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return new Decision(previous.entryId(), List.of(), true);
        }

        JournalEntry original = entries.get(command.originalEntryId());
        if (original == null) {
            throw new EntryNotFoundException(command.originalEntryId());
        }
        if (reversedEntries.contains(original.entryId())) {
            throw new AlreadyReversedException(original.entryId());
        }

        UUID reversalId = StableIds.uuid("ledger-entry:" + ledgerId, command.idempotencyKey());
        JournalEntry reversal = original.reversed(reversalId, command.idempotencyKey(), command.bookedAt(),
                command.reason());
        JournalEntryPosted event = new JournalEntryPosted(
                StableIds.uuid("ledger-event:" + ledgerId, reversalId.toString()), ledgerId,
                command.bookedAt(), reversal, fingerprint);
        return new Decision(reversalId, List.of(event), false);
    }

    public void apply(LedgerEvent event) {
        Objects.requireNonNull(event, "event");
        requireLedger(event.ledgerId());
        if (event instanceof JournalEntryPosted posted) {
            JournalEntry entry = posted.entry();
            if (entries.putIfAbsent(entry.entryId(), entry) != null) {
                throw new IllegalStateException("duplicate entry event: " + entry.entryId());
            }
            IdempotencyRecord prior = idempotency.putIfAbsent(entry.idempotencyKey(),
                    new IdempotencyRecord(posted.requestFingerprint(), entry.entryId()));
            if (prior != null) {
                throw new IllegalStateException("duplicate idempotency key in event history: " + entry.idempotencyKey());
            }
            entry.reversalOfOptional().ifPresent(reversedEntries::add);
            version++;
        }
    }

    public BigDecimal accountBalance(String accountId, CurrencyCode currency) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        BigDecimal balance = BigDecimal.ZERO;
        for (JournalEntry entry : entries.values()) {
            for (Posting posting : entry.postings()) {
                if (posting.accountId().equals(accountId) && posting.currency().equals(currency)) {
                    balance = posting.side() == Side.DEBIT ? balance.add(posting.amount()) : balance.subtract(posting.amount());
                }
            }
        }
        return balance.stripTrailingZeros();
    }

    public List<JournalEntry> entries() {
        return List.copyOf(entries.values());
    }

    public long version() {
        return version;
    }

    private void requireLedger(String candidate) {
        if (!ledgerId.equals(candidate)) {
            throw new IllegalArgumentException("event/command ledger does not match aggregate");
        }
    }

    private static String fingerprint(PostEntryCommand command) {
        StringBuilder canonical = new StringBuilder("post|")
                .append(command.ledgerId()).append('|').append(command.bookedAt()).append('|')
                .append(command.description()).append('|');
        for (Posting posting : command.postings()) {
            canonical.append(posting.accountId()).append(':').append(posting.side()).append(':')
                    .append(posting.currency()).append(':')
                    .append(posting.amount().stripTrailingZeros().toPlainString()).append(';');
        }
        return StableIds.sha256Hex(canonical.toString());
    }

    private static String fingerprint(ReverseEntryCommand command) {
        return StableIds.sha256Hex("reverse|" + command.ledgerId() + '|' + command.originalEntryId() + '|'
                + command.bookedAt() + '|' + command.reason());
    }

    private static String requireLedgerId(String value) {
        Objects.requireNonNull(value, "ledgerId");
        value = value.trim();
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("ledgerId must contain 1..128 characters");
        }
        return value;
    }

    private record IdempotencyRecord(String fingerprint, UUID entryId) { }
}
