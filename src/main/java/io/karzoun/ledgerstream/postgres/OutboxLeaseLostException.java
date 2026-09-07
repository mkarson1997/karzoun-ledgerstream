package io.karzoun.ledgerstream.postgres;

import java.util.UUID;

public final class OutboxLeaseLostException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public OutboxLeaseLostException(UUID messageId, String workerId) {
        super("outbox lease not owned by worker " + workerId + " for message " + messageId);
    }
}
