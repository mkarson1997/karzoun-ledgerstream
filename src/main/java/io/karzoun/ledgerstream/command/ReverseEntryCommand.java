package io.karzoun.ledgerstream.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReverseEntryCommand(String ledgerId, UUID originalEntryId, String idempotencyKey,
                                  Instant bookedAt, String reason) {
    public ReverseEntryCommand {
        ledgerId = requireText(ledgerId, "ledgerId", 128);
        Objects.requireNonNull(originalEntryId, "originalEntryId");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
        Objects.requireNonNull(bookedAt, "bookedAt");
        reason = requireText(reason, "reason", 500);
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
