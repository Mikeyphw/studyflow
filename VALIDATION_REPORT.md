# StudyFlow Validation Report

Generated for the Phase 17 annex file storage and portability baseline.

## Source contracts

- Phase 0 through Phase 16 contracts remain present.
- Phase 17 adds private annex file storage and optional annex payloads in `.studyplan` packages.
- The app version advances to `0.17.0-phase17`.

## Devtool note

This report describes source-level checks only. Gradle build, Android lint, packaging, and device
validation remain authoritative on the target machine.

---

# StudyFlow Validation Report

Generated for the combined Phase 22 global search/smart recommendations and Phase 23 privacy/encryption/backup
hardening overlay. The source baseline is confirmed landed Phase 20; pending Phase 21 code is not required.

## Artifact and source validation completed

- Complete historical repository verifier chain: Phase 0 through Phase 20, then Phase 22 and Phase 23.
- Durable phase-version verifier helper, tested with the current `0.23.0-phase23` version.
- Kotlin PSI syntax parsing for the complete source tree.
- XML parsing for manifests, resources, backup rules, and data-extraction rules.
- TOML parsing for `.devtool.toml`.
- English and Portuguese (Brazil) resource-name parity for modified feature resources.
- All 15 Phase 22 Room queries prepared against exported Room schema version 2.
- Schema-backed recommendation fixtures proving archived-linked rows are excluded and topic subqueries are plan-scoped.
- Focused Kotlin compilation for discovery, retention, portability, encryption, and privacy logic.
- Focused Kotlin compilation and execution of 30 tests across all ten new Phase 22/23 test classes.
- Deterministic PBKDF2-HMAC-SHA256/AES-GCM compatibility vector.
- Streaming encryption/decryption, large-input boundary, wrong-password, and ciphertext-tamper checks.
- Per-export identifier unlinkability, within-export foreign-key consistency, and annex payload ID privacy contracts.
- Unencrypted compatibility, privacy-policy, integrity-report, and zero-write dry-run contracts.
- Two promise audits documented in `docs/quality/PHASE_22_23_PROMISE_AUDIT.md`.
- Clean-copy overlay application twice with source-state, output-hash, inventory, ZIP, and staging-path verification.

## Devtool validation plan

`.devtool.toml` configures real Gradle configuration, affected main/test compilation, historical tests, Android resource
processing, affected-module lint, and `:app:assembleDebug` for target `studyflow_android`.

## Validation boundary

The artifact-generation container does not provide the project Android SDK or Gradle 9.5 execution environment.
Android Gradle configuration, production/test compilation, resource merging, lint, APK assembly, installation, and
device behavior are not claimed as locally completed. Devtool validation on the target machine remains authoritative.

## Phase 24

- Source-contract validator chain extended through Phase 24.
- Android Gradle compile/lint/assemble remains a Devtool-side gate.

## Phase 25

- Source-contract validator chain extended through Phase 25.
- Phase 25 promise audit confirms reminder delivery, dynamic widget updates, boot recovery, and final gap closure.
- Android Gradle compile/lint/assemble and notification/widget device smoke checks remain Devtool-side gates.

## Phase 25 r2 validation delta

- Added source contract checks for daily-summary quiet-hour suppression.
- Added source contract checks for the due-review anti-spam reschedule guard.
- Added pure planner tests covering all reminder types inside quiet hours and immediate due-review delivery.


## Phase 28

- Source-contract validator chain extended through Phase 28.
- Added pure backup/restore drill tests for encryption, wrong-password rejection, tamper detection,
  metadata-private annex identity, restore dry-run zero writes, retention truthfulness, and recovery text.
- Android Gradle compile/lint/assemble and real Storage Access Framework drills remain Devtool/device gates.


## Phase 28 r2

- Corrected the double-audit misses: no generated Python cache payloads, no literal NUL bytes, and behavior checks strengthened beyond marker validation.
