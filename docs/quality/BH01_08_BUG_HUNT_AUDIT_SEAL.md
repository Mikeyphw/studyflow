# BH01–BH08 bug-hunt delivery audit seal

This seal audits the final StudyFlow repository against the eight bug-hunt findings rather than treating successful overlay application as proof of delivery.

## Audit result after this hotfix

1. **BH01 — real manual-folder sync:** delivered. The local-folder lane uses Android SAF tree/document I/O, logical Room snapshot capture, annex collection, encrypted `.sfbackup` writes, real folder enumeration, authenticated restore preview, and repository-backed sync history. Automatic sync remains off for this provider.
2. **BH02 — sync-bundle semantic integrity and symmetric budgets:** restored by this hotfix. `database.snapshot` bytes must match the manifest SHA-256; duplicate attachment declarations fail before map/set normalization can hide them; writer and reader share the 2,048-entry, 64 MiB-per-entry, and 512 MiB-total uncompressed limits.
3. **BH03 — installation-unique device identity:** delivered. Missing IDs receive UUID-backed installation identities, persisted legacy IDs remain authoritative, and clearing sync configuration preserves the installation ID.
4. **BH04 — delivered-reminder history ordering:** delivered. Ordered versioned history drives pruning, legacy membership migrates safely, and the newest 256 deliveries are retained.
5. **BH05 — durable sync ledger/history:** delivered. Bounded checkpoint/event histories persist, legacy last-record state migrates, sequence numbers remain monotonic, audit history survives sync-configuration clearing, and corrupt individual rows fail closed without discarding valid rows.
6. **BH06 — WebDAV concurrent index safety:** delivered. Bundle creation is conditional, index creation/update uses HTTP preconditions and ETag CAS with bounded retry, same-process mutations are serialized, and index metadata is bounded and uniqueness-checked.
7. **BH07 — desktop schedule repository parity:** delivered. Desktop mutations preserve Room-equivalent terminal/session ownership, stale-state and candidate validation, atomic mutation boundaries, correct replace counts, and schedule observation across strategy/rule/activity changes.
8. **BH08 — bounded late-reminder reconciliation:** delivered. Exact durable occurrences get only fixed reconciliation slack, unchanged alarms remain installed, and completed/delivered/rescheduled occurrences remain suppressed. The acceptance command is corrected to the repository's real `:app:testReleaseUnitTest` task.

## Gap found by the audit

The final snapshot still contained the exact pre-BH02 codec and test hashes, with no BH02 acceptance/verifier files. BH02 therefore had **not** survived into the final repository even though later BH overlays were present. No missing production implementation was found for BH01 or BH03–BH08.

## Final validation

Source-contract seal:

```sh
python3 scripts/verify_bh01_08_bug_hunt_audit.py
```

Focused Gradle matrix used by the Devtool artifact:

```text
:core:model:test
:core:portability:test
:core:data:test
:core:domain:test
:desktopApp:test
:app:testReleaseUnitTest
```
