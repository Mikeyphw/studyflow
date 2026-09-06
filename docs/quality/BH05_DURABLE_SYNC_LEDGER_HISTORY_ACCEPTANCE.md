# BH05 — Durable sync ledger/history acceptance

BH05 closes the gap between the sync-ledger model and its Preferences DataStore persistence. Before this fix, the model exposed lists of checkpoints and events, but the repository retained only the latest checkpoint and latest event while a counter implied a longer history.

Required guarantees:

1. **Checkpoint history is durable and bounded.** Up to 64 checkpoint records survive repository/process recreation, including upload location/time, restore-preview state, revision, change token, and conflict count.
2. **Event history is durable and bounded.** Up to 128 ordered ledger events survive recreation. Event sequence numbers remain monotonic even after older retained rows are pruned.
3. **Legacy installs migrate without losing their latest facts.** If the versioned history keys are absent, the pre-BH05 `LastCheckpoint*` and `LastLedgerEvent*` keys seed the new history on the next write.
4. **Clearing sync configuration does not erase audit history or installation identity.** Provider/configuration state is cleared while the installation id, first-seen time, bounded checkpoint history, event history, and event sequence counter are retained.
5. **Restore preview is represented in checkpoint history.** Previewing a checkpoint updates the matching retained record, or records the remote checkpoint when it was not already in local history.
6. **Corrupt individual persisted rows are ignored without discarding valid rows.** Version headers prevent accidental decoding of unrelated preference data.

Validation:

- `python3 scripts/verify_bh05_durable_sync_ledger_history.py`
- `python3 scripts/verify_csov06_sync_ledger.py`
- Gradle task `:core:data:test`
