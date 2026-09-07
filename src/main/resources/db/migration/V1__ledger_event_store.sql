CREATE TABLE ledger_streams (
    ledger_id VARCHAR(128) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_events (
    ledger_id VARCHAR(128) NOT NULL REFERENCES ledger_streams(ledger_id) ON DELETE RESTRICT,
    stream_version BIGINT NOT NULL CHECK (stream_version >= 1),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL CHECK (event_type = 'JournalEntryPosted'),
    recorded_at TIMESTAMPTZ NOT NULL,
    entry_id UUID NOT NULL UNIQUE,
    idempotency_key VARCHAR(200) NOT NULL,
    booked_at TIMESTAMPTZ NOT NULL,
    description VARCHAR(500) NOT NULL,
    reversal_of UUID NULL,
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (ledger_id, stream_version),
    UNIQUE (ledger_id, idempotency_key)
);

CREATE TABLE ledger_postings (
    ledger_id VARCHAR(128) NOT NULL,
    stream_version BIGINT NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    account_id VARCHAR(128) NOT NULL,
    side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    PRIMARY KEY (ledger_id, stream_version, ordinal),
    FOREIGN KEY (ledger_id, stream_version)
        REFERENCES ledger_events(ledger_id, stream_version) ON DELETE RESTRICT
);

CREATE TABLE ledger_outbox (
    message_id UUID PRIMARY KEY,
    ledger_id VARCHAR(128) NOT NULL,
    stream_version BIGINT NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    locked_by VARCHAR(128) NULL,
    locked_until TIMESTAMPTZ NULL,
    published_at TIMESTAMPTZ NULL,
    FOREIGN KEY (ledger_id, stream_version)
        REFERENCES ledger_events(ledger_id, stream_version) ON DELETE RESTRICT,
    FOREIGN KEY (event_id) REFERENCES ledger_events(event_id) ON DELETE RESTRICT
);

CREATE INDEX idx_ledger_outbox_claim
    ON ledger_outbox (occurred_at, message_id)
    WHERE published_at IS NULL;

CREATE INDEX idx_ledger_events_entry_lookup
    ON ledger_events (ledger_id, entry_id);
