package io.karzoun.ledgerstream.policy;

import java.time.Instant;

@FunctionalInterface
public interface PostingPeriodPolicy {
    void requireOpen(Instant bookedAt);

    static PostingPeriodPolicy alwaysOpen() {
        return bookedAt -> { };
    }
}
