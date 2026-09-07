## Summary

Describe the change and why it is needed.

## Validation

- [ ] `mvn -B -ntp verify` passes
- [ ] accounting/concurrency invariant tests were added or updated where relevant
- [ ] no real financial/customer data or credentials were added
- [ ] no unsupported banking/compliance/performance claim was introduced

## Ledger semantics

If this changes posting, idempotency, reversal, replay, optimistic concurrency, or outbox semantics, explain the failure behavior and tests that prove it.
