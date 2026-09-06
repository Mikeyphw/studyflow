# BH04 — Reminder delivered-history ordering acceptance

BH04 closes the delivered-occurrence pruning bug in the Android reminder journal.

## Closed contracts

1. **Delivery order is durable.** Delivered occurrence IDs are persisted in a versioned ordered string representation instead of relying on `SharedPreferences.StringSet` iteration order.
2. **Pruning is chronological from the journal's perspective.** Once the 256-entry cap is exceeded, the oldest recorded delivery is removed and the newest 256 remain. Re-recording an existing occurrence refreshes it to newest.
3. **Existing installs retain suppression history.** The old `delivered:<planId>` StringSet remains readable and is migrated on first access. Because the historical set never stored chronology, its membership is preserved as one legacy block; all subsequent deliveries have deterministic order.
4. **Recovery/downgrade membership remains available.** The legacy StringSet is retained as a membership mirror, but never used as an ordering source while the ordered key is valid.
5. **Plan cleanup removes both representations.** `clearPlan()` clears scheduled state, legacy delivered membership, ordered delivered history, and the pipeline canary.

## Focused validation

- `python3 scripts/verify_bh04_reminder_delivered_history.py`
- `./gradlew :app:testReleaseUnitTest --no-daemon`

Regression coverage checks true-oldest eviction, re-delivery recency refresh, lossless versioned encoding, legacy membership migration, and source wiring that prevents `markDelivered()` from pruning a `StringSet`.
