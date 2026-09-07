package io.karzoun.ledgerstream.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record JournalEntry(UUID entryId, String idempotencyKey, Instant bookedAt, String description,
                           List<Posting> postings, UUID reversalOf) {
    private static final int MAX_IDEMPOTENCY_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_POSTINGS = 1_000;

    public JournalEntry {
        Objects.requireNonNull(entryId, "entryId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_LENGTH);
        Objects.requireNonNull(bookedAt, "bookedAt");
        description = description == null ? "" : description.trim();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description exceeds " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
        if (postings.size() < 2 || postings.size() > MAX_POSTINGS) {
            throw new IllegalArgumentException("journal entry must contain 2.." + MAX_POSTINGS + " postings");
        }
        requireBalanced(postings);
        if (entryId.equals(reversalOf)) {
            throw new IllegalArgumentException("an entry cannot reverse itself");
        }
    }

    public Optional<UUID> reversalOfOptional() {
        return Optional.ofNullable(reversalOf);
    }

    public JournalEntry reversed(UUID newEntryId, String newIdempotencyKey, Instant reversalBookedAt,
                                 String reversalDescription) {
        List<Posting> reversedPostings = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            reversedPostings.add(posting.reversed());
        }
        return new JournalEntry(newEntryId, newIdempotencyKey, reversalBookedAt, reversalDescription,
                reversedPostings, entryId);
    }

    public static void requireBalanced(List<Posting> postings) {
        Map<CurrencyCode, BigDecimal> debits = new LinkedHashMap<>();
        Map<CurrencyCode, BigDecimal> credits = new LinkedHashMap<>();
        for (Posting posting : postings) {
            Map<CurrencyCode, BigDecimal> target = posting.side() == Side.DEBIT ? debits : credits;
            target.merge(posting.currency(), posting.amount(), BigDecimal::add);
        }
        for (CurrencyCode currency : unionCurrencies(debits, credits)) {
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            if (debit.compareTo(credit) != 0) {
                throw new UnbalancedEntryException(currency, debit, credit);
            }
        }
    }

    private static List<CurrencyCode> unionCurrencies(Map<CurrencyCode, BigDecimal> debits,
                                                       Map<CurrencyCode, BigDecimal> credits) {
        List<CurrencyCode> currencies = new ArrayList<>(debits.keySet());
        for (CurrencyCode currency : credits.keySet()) {
            if (!currencies.contains(currency)) {
                currencies.add(currency);
            }
        }
        Collections.sort(currencies);
        return currencies;
    }

    private static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        value = value.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1.." + maxLength + " characters");
        }
        return value;
    }
}
