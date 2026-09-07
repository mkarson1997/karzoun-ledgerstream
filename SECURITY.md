# Security policy

Please do not publish an exploit for a vulnerability involving ledger corruption, idempotency bypass, concurrency failure, unsafe persistence, or supply-chain compromise. Use GitHub private vulnerability reporting when available, or the repository owner's published private contact channel.

## Current boundary

The v0.1 core has no network listener and no durable database adapter. It must not be described as hardened for hostile multi-tenant internet exposure.

Current security-sensitive invariants include balanced entries per currency, positive decimal-only amounts, immutable history, payload-bound idempotency, optimistic expected-version checks, and the atomic event/outbox adapter boundary.

Future network and database milestones require separate threat modeling, authentication, authorization, query limits, migration safety, secret handling, and recovery testing.
