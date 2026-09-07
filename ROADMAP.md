# LedgerStream roadmap

## v0.1 accounting core

- [x] immutable double-entry journal entries
- [x] per-currency balancing
- [x] `BigDecimal` amount model
- [x] payload-bound idempotency semantics
- [x] append-only reversal entries
- [x] deterministic replay
- [x] optimistic-concurrency store contract
- [x] event + outbox atomicity boundary
- [x] accounting-period policy boundary
- [x] concurrent writer conflict test
- [x] Java 21/25 CI and CodeQL

## v0.2 PostgreSQL durability

- [ ] Flyway schema migrations
- [ ] PostgreSQL event-stream adapter
- [ ] atomic expected-version enforcement
- [ ] transactional outbox table and claim lifecycle
- [ ] unique idempotency constraints as defense in depth
- [ ] Testcontainers integration suite
- [ ] process-restart reconstruction tests
- [ ] rollback/failure-injection tests

## v0.3 accounting operations

- [ ] chart-of-accounts policies
- [ ] durable period close/reopen audit events
- [ ] trial-balance projection
- [ ] configured currency precision/rounding policies
- [ ] projection checkpoints with replay verification
- [ ] controlled batch posting

## v0.4 service boundary

- [ ] authenticated API only after durable storage is proven
- [ ] scoped authorization and request limits
- [ ] OpenTelemetry traces and Prometheus metrics
- [ ] outbox publisher with delivery/lag observability
- [ ] graceful shutdown and readiness semantics

No banking-grade, compliance, throughput, latency, or recovery claims without measured or externally verifiable evidence.
