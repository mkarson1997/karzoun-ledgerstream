package io.karzoun.ledgerstream.policy;

import java.time.Instant;
import java.util.Objects;

public final class ClosedBeforePolicy implements PostingPeriodPolicy {
    private final Instant openFrom;

    public ClosedBeforePolicy(Instant openFrom) {
        this.openFrom = Objects.requireNonNull(openFrom, "openFrom");
    }

    @Override
    public void requireOpen(Instant bookedAt) {
        Objects.requireNonNull(bookedAt, "bookedAt");
        if (bookedAt.isBefore(openFrom)) {
            throw new ClosedPeriodException(bookedAt, openFrom);
        }
    }
}
