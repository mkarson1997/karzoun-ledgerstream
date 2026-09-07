# Contributing

LedgerStream uses an evidence-first workflow.

1. Open or reference a focused issue for non-trivial work.
2. Work on a branch.
3. Run `mvn -B -ntp verify`.
4. Add tests for changed accounting or concurrency invariants.
5. Open a pull request and wait for CI/security checks.

Durable adapters must preserve `appendAtomically`: event insertion and outbox insertion commit or roll back together, and stale expected versions must never partially write.

Do not add real customer ledgers, personal financial data, credentials, bank data, API keys, or production database dumps. Fixtures must be synthetic.
