# Changelog

All notable changes to LedgerStream are documented here.

## [0.1.0] - 2026-09-07

### Added

- immutable Java 21 double-entry journal domain
- per-currency balance validation using `BigDecimal`
- payload-bound idempotent posting and reversal semantics
- deterministic event-sourced aggregate reconstruction
- append-only reversal entries
- accounting-period policy boundary
- Flyway-managed PostgreSQL schema
- PostgreSQL event-store adapter with optimistic expected-version enforcement
- atomic event, postings, and outbox transaction boundary
- leased transactional outbox claims using `FOR UPDATE SKIP LOCKED`
- Testcontainers restart reconstruction and forced rollback tests
- Java 21/25 CI, PostgreSQL integration gate, CodeQL, and Dependabot
- Maven sources artifact and CycloneDX SBOM release packaging

[0.1.0]: https://github.com/mkarson1997/karzoun-ledgerstream/releases/tag/v0.1.0
