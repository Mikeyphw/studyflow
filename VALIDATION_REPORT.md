# StudyFlow Validation Report

Generated for the source state implemented through **Phase 37**.

## Source-contract status

The repository contract chain is expected to run through:

- Phase validators `scripts/verify_phase_0.py` through `scripts/verify_phase_37.py`
- Debug Workbench validators D1 through D8
- `scripts/verify_full_app_final_user_readiness.py`

Phase 34 seals interaction honesty by replacing passive-but-clickable UI chips with the shared
`StudyFlowStatusPill` display component and by rejecting empty click handlers in production source.

Phase 35 seals documentation and artifact hygiene by requiring a single current README, a single
current changelog heading, a current validation report, no stale generated Devtool state in overlay
payloads, and no archived release-checklist language in normal user resources.

Phase 36 seals release identity and packaging by moving the app to `1.0.0-rc1`, documenting the
signing boundary, documenting the release-candidate package path, and keeping minification disabled
until a dedicated shrinking validation pass exists.

Phase 37 seals the real-device acceptance framework by adding a target-execution checklist and a
pending final acceptance report. Source validation must not mark install, upgrade, notification,
widget, backup, restore, locale, theme, or accessibility checks as passed.

## Artifact-state cleanup

Phase 35 r3 keeps overlay payloads free of stale Devtool reports, validation state, caches, and generated output. Devtool reserves `.devtool/` runtime metadata and rejects overlay inventory entries that target it, including delete entries. Existing stale local Devtool state is therefore cleaned outside the overlay with an explicit local command when needed.

## Target-machine gates

The source-contract report is not a substitute for Android target validation. These gates remain
Devtool or Android-build-host responsibilities:

- Gradle configuration resolution
- affected Kotlin/JVM/Android compilation
- Android resource merge
- lint with zero unexpected diagnostics
- unit test execution
- Room schema and migration validation
- APK assembly for `1.0.0-rc1`
- signing or debug/internal distribution packaging with external credentials
- install/upgrade smoke on a real device or emulator
- notification, reminder, widget, backup, restore-dry-run, language, theme, and accessibility checks

## Current known boundary

The app is offline-first. Local checkpoint status is a device-local backup/readiness surface, not an
online sync provider. The checked-in final acceptance report is a **PENDING TARGET EXECUTION**
template until Devtool or a manual device pass fills it with real evidence.

## Artifact hygiene contract

Deliverable overlays must not include build outputs, Gradle caches, Python caches, generated Devtool
state, old reports, NUL bytes, or inventory targets under `.devtool-artifact/*`. Embedded artifact
validators may live under `.devtool-artifact/`, but those metadata files are not copied into the
repository.
