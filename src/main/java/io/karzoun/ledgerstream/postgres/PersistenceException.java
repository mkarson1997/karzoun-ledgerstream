package io.karzoun.ledgerstream.postgres;

public final class PersistenceException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
