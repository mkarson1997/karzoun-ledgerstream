# LedgerStream architecture

LedgerStream treats accounting history as an immutable event stream. Commands are decided against reconstructed state and accepted decisions append new events rather than mutate old records.

## Command flow

```text
PostEntry / ReverseEntry
        |
        v
load PostgreSQL event stream + version
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
        |
        v
one PostgreSQL transaction
```

## Double-entry rule

Balancing is checked independently per `CurrencyCode`. Every posting has a positive decimal amount and an explicit `DEBIT` or `CREDIT` side. Multi-currency journal entries therefore require balanced legs for every currency they contain.

## Idempotency

`JournalEntryPosted` persists the request fingerprint. Replay reconstructs the idempotency map from durable history.

- exact retry: return the original entry ID and append zero events
- changed payload under an existing key: reject
- PostgreSQL uniqueness on `(ledger_id, idempotency_key)`: defense in depth against adapter/application defects

## Reversals

A reversal appends a new journal entry with inverted posting sides and a `reversalOf` reference. Original history remains untouched.

## Optimistic concurrency

Each ledger has a monotonically increasing stream version. The PostgreSQL adapter advances the version only when the stored version equals the caller's expected version. A stale writer receives `ConcurrencyException`.

The application does not silently retry changed decisions. Callers may retry the original idempotent command, causing a fresh replay and decision.

## PostgreSQL transaction boundary

One `appendAtomically` call performs:

1. ensure stream row exists;
2. compare-and-advance stream version;
3. insert event metadata;
4. insert ordered postings;
5. insert one outbox row per event;
6. commit.

Any SQL failure rolls back every step. Integration tests induce an outbox primary-key violation after earlier writes and verify no partial version/event/posting change survives.

## Transactional outbox

Unpublished rows are claimed with `FOR UPDATE SKIP LOCKED`, bounded batch sizes, and leases capped at one hour. Claims increment attempt count. `markPublished` and `release` succeed only for the worker that owns the active claim.

## Schema evolution

Flyway owns the database schema. Migrations live under `src/main/resources/db/migration` and are exercised against a real PostgreSQL container in CI.

## Service boundary

v0.1.0 intentionally has no HTTP server. Authentication, authorization, rate limits, API semantics, telemetry, and service-container packaging remain a later milestone so the persistence/accounting core can be proven first.
