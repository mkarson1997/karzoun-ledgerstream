package io.karzoun.ledgerstream.aggregate;

public final class IdempotencyConflictException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String key) {
        super("idempotency key was already used with a different payload: " + key);
    }
}
