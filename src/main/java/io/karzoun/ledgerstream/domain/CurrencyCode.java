package io.karzoun.ledgerstream.domain;

import java.util.Locale;
import java.util.Objects;

public record CurrencyCode(String value) implements Comparable<CurrencyCode> {
    public CurrencyCode {
        Objects.requireNonNull(value, "value");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter ISO-style code");
        }
    }

    @Override
    public int compareTo(CurrencyCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
