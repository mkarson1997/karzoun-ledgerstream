# Changelog

## Unreleased

### Added

- immutable Java 21 double-entry journal domain
- per-currency balance validation using `BigDecimal`
- idempotent posting/reversal semantics with payload conflict detection
- deterministic event-sourced aggregate reconstruction
- append-only reversal entries
- accounting-period policy boundary
- optimistic-concurrency event-store contract
- explicit atomic event/outbox persistence boundary
- reference in-memory store
- deterministic accounting, replay, reversal, idempotency, and concurrency tests
- Java 21/25 CI, CodeQL, and Dependabot
