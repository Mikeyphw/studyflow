# BH02 Sync Bundle Hardening Acceptance

BH02 closes three correctness gaps in the `.sfbackup` codec without changing the sync bundle format or schema version.

## Closed contracts

- `database.snapshot` bytes are SHA-256 hashed at both write and read boundaries and must match `manifest.databaseChecksumSha256`; checkpoint and manifest metadata agreeing with each other is no longer sufficient.
- Writer preflight uses the same 2,048-entry, 64 MiB-per-entry, and 512 MiB-total uncompressed limits enforced by the reader, so StudyFlow cannot intentionally emit a bundle that its own reader rejects for those budgets.
- Attachment declarations must have unique ZIP entry names before source-key set/map normalization can hide duplicates.

## Regression coverage

`StudyFlowSyncBundleCodecTest` covers forged-but-integrity-consistent database metadata on read, false database metadata on write, duplicate attachment declarations on both boundaries, and writer-side per-entry, entry-count, and total-byte budget rejection.

Run:

```bash
python3 scripts/verify_bh02_sync_bundle_hardening.py
python3 scripts/verify_csov02_sync_bundle.py
./gradlew :core:portability:test --no-daemon
```
