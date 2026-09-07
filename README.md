# Karzoun LedgerStream

[![CI](https://github.com/mkarson1997/karzoun-ledgerstream/actions/workflows/ci.yml/badge.svg)](https://github.com/mkarson1997/karzoun-ledgerstream/actions/workflows/ci.yml)
[![CodeQL](https://github.com/mkarson1997/karzoun-ledgerstream/actions/workflows/codeql.yml/badge.svg)](https://github.com/mkarson1997/karzoun-ledgerstream/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

LedgerStream is a Java 21 double-entry ledger engine focused on accounting invariants, append-only history, idempotent command handling, optimistic concurrency, and deterministic reconstruction.

## v0.1 core scope

The current foundation deliberately separates domain semantics from database/framework integration. It provides immutable journal entries, per-currency debit/credit balancing using `BigDecimal`, payload-bound idempotency, append-only reversals, deterministic replay, optimistic concurrency, an atomic event+outbox persistence boundary, accounting-period policy, and deterministic tests.

The in-memory store is a contract/reference adapter for the first milestone. It is **not durable storage**. PostgreSQL, Flyway migrations, transactional row locking, and Testcontainers are the next persistence milestone.

## Accounting invariants

Every represented currency balances independently: `sum(debits, currency) == sum(credits, currency)`. Amounts are positive decimal values and never binary floating point.

## Idempotency

Same key + same payload is a no-op replay returning the original entry ID. Same key + changed payload is rejected. The mapping is reconstructed from event history rather than a process-local cache.

## Reversals

Historical entries are never edited or deleted. A reversal appends a new balanced entry with inverted posting sides and a `reversalOf` reference.

## Optimistic concurrency and outbox

`EventStore.appendAtomically(...)` requires an expected stream version and receives ledger events and outbox messages in one call. Durable adapters must commit both sets or neither set.

## Build

Requires JDK 21+ and Maven 3.9+.

```bash
mvn -B -ntp verify
```

CI verifies Java 21 and Java 25.

See [`docs/architecture.md`](docs/architecture.md) and [`ROADMAP.md`](ROADMAP.md).

## Non-claims

LedgerStream does not currently claim to be banking-grade, certified accounting software, or suitable for regulated production use.

## License

Apache-2.0.
