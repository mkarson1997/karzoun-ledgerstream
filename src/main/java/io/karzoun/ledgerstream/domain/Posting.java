package io.karzoun.ledgerstream.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Posting(String accountId, Side side, CurrencyCode currency, BigDecimal amount) {
    private static final int MAX_SCALE = 18;
    private static final int MAX_ACCOUNT_ID_LENGTH = 128;

    public Posting {
        accountId = requireText(accountId, "accountId", MAX_ACCOUNT_ID_LENGTH);
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        amount = amount.stripTrailingZeros();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("posting amount must be positive");
        }
        if (Math.max(amount.scale(), 0) > MAX_SCALE) {
            throw new IllegalArgumentException("posting amount scale exceeds " + MAX_SCALE);
        }
    }

    public Posting reversed() {
        return new Posting(accountId, side.opposite(), currency, amount);
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
