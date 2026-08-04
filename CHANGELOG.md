# Changelog

## Phase 36-37 release identity and real-device acceptance seal

- Moved the application identity to `versionCode = 37` and `versionName = "1.0.0-rc1"`.
- Updated phase-version validation so release-candidate versioning remains compatible with historical source-contract validators.
- Documented the release-candidate package path, signing boundary, minification decision, and external-secret policy.
- Added `FINAL_ACCEPTANCE_REPORT.md` as a pending target-execution template instead of fake device evidence.
- Added a real-device acceptance checklist covering fresh install, upgrade install, sessions, practice, reminders, widgets, backup, restore dry-run, locale, theme, background/foreground, kill/reopen, date/time, and accessibility checks.
- Advanced release readiness, validation seals, source scripts, and repository contract loops through Phase 37 while keeping target-machine gates blocked until real evidence exists.

## Phase 34-35 interaction honesty and documentation cleanup

- r3: Corrected the overlay contract so it no longer targets Devtool-reserved `.devtool/` runtime metadata. Generated Devtool workspace state must be cleaned with an explicit local command when needed, not through overlay inventory.

- Added a shared `StudyFlowStatusPill` display component for passive status labels.
- Replaced passive `AssistChip(onClick = {})` controls across Library, Reviews, Runtime Smoke,
  Progress, Sessions, Reminders, Analytics, Schedule, Plans, Subjects, and Practice screens.
- Removed remaining online-sync/security negation copy from the user-facing Privacy surface.
- Reworded developer diagnostic compatibility labels away from legacy scaffold language.
- Advanced source validation, app version, release readiness, and repository contract loops through
  Phase 35.
- Rewrote README, validation report, and changelog as current product docs with one top-level heading.

## Phase 32-33 diagnostics de-scaffolding and offline checkpoint completion

- Renamed the debug entry to Developer diagnostics and kept it gated to debug builds.
- Replaced fallback probe/lab placeholder copy with concrete developer-support fallback states.
- Completed the former sync foundation as an offline-first local checkpoint surface that keeps the
  user story on this device.
- Reworked the local checkpoint model around checkpoint readiness and pending local changes instead
  of online transfer selection.
- Advanced release readiness and source validation through Phase 33.

## Phase 30-31 product truthfulness and dashboard action completion

- Removed normal-user Settings cards that exposed app-shell readiness, UX polish, release-candidate
  polish, and stale release seals.
- Renamed local-only sync surfaces to local backup checkpoints and removed user-facing online-sync
  promises.
- Advanced the release-readiness scope through Phase 31 plus Debug Workbench D1-D8 while keeping
  target-machine release gates evidence-based.
- Wired the Dashboard primary session action to the existing Study Sessions route and converted the
  default-plan chip into a passive badge.
- Added Phase 30 and Phase 31 verifiers plus source-level contract tests for Settings and Dashboard
  truthfulness.

## Earlier delivery history

Detailed phase and Debug Workbench implementation notes remain in the root `PHASE_*`,
`DEBUG_WORKBENCH_*`, and `docs/quality/*` files because historical validators still treat those files
as source contracts. They are retained as implementation history, not as current user-facing product
copy.
