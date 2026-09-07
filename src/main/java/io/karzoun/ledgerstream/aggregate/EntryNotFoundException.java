package io.karzoun.ledgerstream.aggregate;

import java.util.UUID;

public final class EntryNotFoundException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public EntryNotFoundException(UUID entryId) {
        super("journal entry not found: " + entryId);
    }
}
