# LedgerStream architecture

LedgerStream treats accounting history as an immutable event stream. Commands are validated against reconstructed state, and accepted decisions append new events rather than mutate old records.

## Command flow

```text
PostEntry / ReverseEntry
        |
        v
load event stream + version
        |
        v
replay LedgerAggregate
        |
        v
period policy + accounting invariants + idempotency checks
        |
        v
Decision(events | idempotent no-op)
        |
        v
appendAtomically(expectedVersion, events, outbox)
```

## Double-entry rule

Balancing is checked independently per `CurrencyCode`. Every posting has a positive decimal amount and an explicit `DEBIT` or `CREDIT` side. A multi-currency journal entry therefore needs balanced legs for each currency it contains.

## Idempotency model

The aggregate reconstructs idempotency state from each `JournalEntryPosted` event. Identical retries append nothing and return the existing entry ID. A changed payload under an existing key is rejected.

## Reversal model

A reversal never mutates an original event. It appends a new `JournalEntry` with every posting side inverted and a `reversalOf` reference to the original entry ID.

## Optimistic concurrency

The store loads `(version, events)` and requires append against that exact version. A stale writer receives `ConcurrencyException`. The application deliberately does not hide that conflict with an unsafe implicit retry.

## Transactional outbox boundary

Every appended ledger event has one `OutboxMessage`. Durable adapters must persist the event stream update and outbox messages in the same database transaction.

## Durability boundary

Current state is process-local. It demonstrates accounting and concurrency semantics, not crash durability. PostgreSQL, Flyway, recovery tests, and Testcontainers are the next milestone.
