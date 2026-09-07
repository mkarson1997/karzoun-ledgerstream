package io.karzoun.ledgerstream.command;

import io.karzoun.ledgerstream.domain.Posting;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PostEntryCommand(String ledgerId, String idempotencyKey, Instant bookedAt,
                               String description, List<Posting> postings) {
    public PostEntryCommand {
        ledgerId = requireText(ledgerId, "ledgerId", 128);
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
        Objects.requireNonNull(bookedAt, "bookedAt");
        description = description == null ? "" : description.trim();
        postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
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
