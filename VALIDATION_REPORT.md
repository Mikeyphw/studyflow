# StudyFlow Validation Report

Generated for the source state implemented through **Phase 35**.

## Source-contract status

The repository contract chain is expected to run through:

- Phase validators `scripts/verify_phase_0.py` through `scripts/verify_phase_35.py`
- Debug Workbench validators D1 through D8
- `scripts/verify_full_app_final_user_readiness.py`

Phase 34 seals interaction honesty by replacing passive-but-clickable UI chips with the shared
`StudyFlowStatusPill` display component and by rejecting empty click handlers in production source.

Phase 35 seals documentation and artifact hygiene by requiring a single current README, a single
current changelog heading, a current validation report, no stale generated Devtool state in overlay
payloads, and no archived release-checklist language in normal user resources.

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
- APK assembly
- install/upgrade smoke on a real device or emulator
- notification, reminder, widget, backup, restore-dry-run, language, theme, and accessibility checks

## Current known boundary

The app is offline-first. Local checkpoint status is a device-local backup/readiness surface, not an
online sync provider. Release packaging, signed artifacts, release version naming, and final device
acceptance evidence are intentionally reserved for the release-packaging phase.

## Artifact hygiene contract

Deliverable overlays must not include build outputs, Gradle caches, Python caches, generated Devtool
state, old reports, NUL bytes, or inventory targets under `.devtool-artifact/*`. Embedded artifact
validators may live under `.devtool-artifact/`, but those metadata files are not copied into the
repository.
