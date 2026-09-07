# LedgerStream roadmap

## v0.1.0 accounting + durability

- [x] immutable double-entry journal entries
- [x] per-currency balancing with `BigDecimal`
- [x] payload-bound idempotency
- [x] append-only reversals
- [x] deterministic event replay
- [x] accounting-period policy boundary
- [x] Flyway-managed PostgreSQL schema
- [x] durable PostgreSQL event store
- [x] atomic expected-version enforcement
- [x] event + postings + outbox in one SQL transaction
- [x] database uniqueness constraints
- [x] Testcontainers restart and rollback evidence
- [x] leased outbox claims with `FOR UPDATE SKIP LOCKED`
- [x] Java 21/25 CI and CodeQL

## v0.2 accounting operations

- [ ] explicit chart of accounts and account-type policies
- [ ] durable period close/reopen audit events
- [ ] trial-balance projection
- [ ] configured currency precision and rounding policies
- [ ] projection checkpoints with replay verification
- [ ] controlled batch posting

## v0.3 service runtime

- [ ] authenticated API
- [ ] scoped authorization
- [ ] request/body/rate limits
- [ ] OpenTelemetry traces and metrics
- [ ] Prometheus operational metrics
- [ ] outbox publisher with lag/delivery observability
- [ ] graceful shutdown and readiness semantics
- [ ] container image only after service runtime exists

## v0.4 resilience and evidence

- [ ] database failure injection beyond rollback constraints
- [ ] migration compatibility tests
- [ ] backup/restore recovery runbook and automated verification
- [ ] measured posting/replay/outbox throughput and p50/p95/p99 latency
- [ ] security threat model for internet-facing runtime

No banking-grade, compliance, throughput, latency, or recovery claims without measured or externally verifiable evidence.
