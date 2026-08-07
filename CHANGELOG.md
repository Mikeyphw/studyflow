# Phase 56-57 r2 promise-closure audit

- Added `verifyPhase56_57ReleaseReadiness` as a release-facing source gate.
- Added `:app:lintRelease` to the overlay validation task list.
- Updated the Phase 56/57 verifier, acceptance docs, README, and validation report so release-mode wording no longer rests on debug-only validation.

# Phase 56-57 Post-V2 Integration and Real-Device Polish

- Added a Post-V2 Integration Seal covering the full UI/UX V2 workspace, focus, readiness, reschedule, practice, mock exam, widget, reminder, timeline, backup, search, and localization chain.
- Added a Device Readiness Polish Seal for compact portrait behavior, large fonts, TalkBack-facing labels, predictive back, edge-to-edge, widget taps, reminder permission states, upgrade/backup gates, and warning boundaries.
- Added repository verification for the known post-V2 traps: route loops, missing workspace wiring, android:tint, bad resource escapes, deprecated TrendingUp icon usage, duplicate strings, and forbidden Kotlin extension references.

# Changelog

## Phase 54-55 reminder reliability and evidence timeline

- Added a Reminder Reliability Center that separates blocked reminder delivery, degraded timing, backlog pressure, quiet hours, and safe fallback behavior.
- Added a V2 Progress Evidence Timeline from sessions, planned blocks, reviews, questions, practice tests, mock exams, completed goals, and pinned notes.
- Kept the timeline localized in the UI and bounded/newest-first in the domain.
- Added Phase 54/55 tests, documentation, and verifier wiring.

## Phase 52-53 r4 Mock Exam Studio compile hotfix

- Replaced the `Question::isExamEligible` extension reference in `MockExamStudioPlanner` with an explicit lambda so Kotlin/JVM compilation accepts the planner.
- Hardened the Phase 52/53 verifier so the prohibited extension-function reference cannot return.


## Phase 52-53 - Mock Exam Studio and widget actions

- Added a preview-first Mock Exam Studio for timed simulated exams.
- Added localized Practice workspace controls for mock-exam creation.
- Upgraded the Today widget with plan-aware primary and review actions.
- Phase 52/53 r2: tightened Mock Exam Studio filtering, disputed-question handling, dynamic preview, and warning display.

## Phase 50-51 r3 Kotlin compile hotfix

- Fixed `PracticeWorkspaceScreen.reviewTitle` to snapshot cross-module nullable review IDs into local values before smart-casting.
- Added a source-contract gate that rejects direct smart-casts of public `ReviewItem` properties from another module.
- Preserved the complete Phase 50-51 r2 weakness-drill and question-import implementation.

## Phase 50-51 r2 audit hotfix

- Fixed the question importer to use valid escaped Kotlin regexes and no literal tab characters in source.
- Fixed TSV/Anki-style rows without options to import as short-answer questions.
- Fixed weakness-drill scoring so recurrence weight cannot pull unrelated questions into a generated drill.
- Fixed import rejected-count math to count rejected drafts rather than individual issue rows.
- Added focused tests and verifier checks for the audit gaps.

## Phase 50-51 weakness drills and question import

- Added a weakness-drill generator that builds targeted practice from repeated errors, overdue question reviews, matching topics, and matching subjects.
- Added a question import pipeline for CSV, TSV/Anki-style rows, and Markdown blocks with validation and duplicate detection.
- Wired weakness-drill creation and text question import into the V2 Practice workspace.
- Fixed remaining AutoMirrored `TrendingUp` deprecation warnings in the V2 Progress workspace.
- Added domain tests, bilingual workspace resources, documentation, and a Phase 50-51 verifier.

## Phase 48-49 readiness and adaptive reschedule

- Added an explainable exam readiness score combining coverage, review health, practice, mock exams, consistency, schedule adherence, and time feasibility.
- Added a preview-first adaptive reschedule wizard that updates only executable unlocked planned activities.
- Surfaced readiness signals and safe reschedule previews inside the V2 Progress workspace.
- Added domain tests, bilingual resources, documentation, and a Phase 48-49 repository verifier.

## Phases 38-47 complete UI/UX V2 workspace migration

- Replaced the feature-catalog shell with adaptive Today, Plan, Practice, and Progress roots.
- Added saved root back stacks, typed lightweight routes, legacy-route translation, and distinct full-editor routes.
- Added repository-backed command-center, plan, practice, progress, plan-picker, system-hub, and contextual quick-add screens.
- Added expandable subject/topic sections, direct V2 review grading and skipping, test/mock visibility, progress trend semantics, and actionable adaptation previews.
- Added persistent focus mode with Room schema 3, migration 1 to 3 / 2 to 3 coverage, notes, pause/resume, completion, cancellation, and restoration.
- Hardened the V2 source contract after audit so it rejects navigation compile defects, marker-only implementation, shell-wide back interception, schema-2 backup regressions, large-font clipping, weak pane semantics, and cross-plan stack leakage.
- Added bilingual workspace resources, reusable design-system components, edge-to-edge setup, tests, source validators, and Devtool gates.
- Preserved the existing release-candidate identity and real-device evidence boundary.

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

## R4 Devtool test-contract hotfix

- Fixed `ExamReadinessEngine.timeFeasibility` so an empty pending schedule is treated as "no explicit backlog" rather than fabricating uncovered work from coverage alone.
- This keeps a well-covered, high-retention plan with strong mock/practice evidence in `ON_TRACK`, matching the domain test and the promised readiness semantics.
- The pessimistic branch still applies when there are real pending planned minutes, and same-day exams still use coverage directly.

## Phase 48-49 r5 workspace resource hotfix

- Added the missing localized `open_plan` workspace string referenced by the Progress readiness surface.
- Hardened the Phase 48-49 verifier so `ProgressWorkspaceScreen` `R.string.*` references must exist in workspace resources.


## Phase 54/55 r3 resource and lint hotfix

- Fixed reminder reliability strings that failed Android resource flattening.
- Added validation guards for risky reminder string escapes and widget `android:tint` lint regressions.
