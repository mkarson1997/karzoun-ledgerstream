package io.karzoun.ledgerstream.policy;

import java.time.Instant;

public final class ClosedPeriodException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ClosedPeriodException(Instant bookedAt, Instant openFrom) {
        super("posting period is closed for " + bookedAt + "; earliest open instant is " + openFrom);
    }
}
