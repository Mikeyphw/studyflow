# SFLP22 + SFLP23 — Account/backup handoff and release validation consolidation

SFLP22 adds account and backup handoff primitives without adding cloud state changing. A trusted peer
can offer account recovery, device replacement, desktop recovery, audit, or backup restore metadata
through SFLP. The handoff carries bounded manifest hashes, target device IDs, TTLs, and capability
requirements; it never carries plaintext credentials or server-owned account secrets.

SFLP23 consolidates the final validation lane for the cross-platform SFLP series. The release gate
names every predecessor verifier from SFLP01 through SFLP22/SFLP23 and includes the platform unit-test
lanes for `:core:link`, Android TV, Desktop, and Android release unit tests. This makes the last run a
single explicit task chain while keeping intermediate overlays on `--no-validate`.

## Guardrails

- `account.handoff_offer` is target-gated, TTL-bound, capability-gated, and duplicate-safe.
- `account.handoff_accept` / `account.handoff_reject` acknowledge the exact delivery sequence.
- `backup.bundle_offer` names bundle kind, item count, hash, size, encryption mode, and restore caps.
- `backup.restore_plan` rejects stale sequences, duplicates, expired bundles, and TV write restores.
- TV/Web-TV surfaces may audit or preview backups, but cannot take account ownership or restore writes.
- `release.validation_plan` records the full SFLP verifier chain and required platform test lanes.
- `release.validation_result` can be converted to a normal SFLP domain operation for audit history.

## Validation

The final gate is `verifySflp22Sflp23AccountBackupValidation`. Intermediate application should still
use `--no-validate`; run the consolidated task chain only when you are ready for the final seal.

SFLP24 final release seal now consumes this gate as its predecessor and runs the complete chain.
