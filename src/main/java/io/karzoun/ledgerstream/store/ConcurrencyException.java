package io.karzoun.ledgerstream.store;

public final class ConcurrencyException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ConcurrencyException(String ledgerId, long expectedVersion, long actualVersion) {
        super("optimistic concurrency conflict for ledger " + ledgerId + ": expected version "
                + expectedVersion + ", actual version " + actualVersion);
    }
}
