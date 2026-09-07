package io.karzoun.ledgerstream.aggregate;

import java.util.UUID;

public final class AlreadyReversedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public AlreadyReversedException(UUID entryId) {
        super("journal entry has already been reversed: " + entryId);
    }
}
