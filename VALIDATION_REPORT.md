# StudyFlow Validation Report

## Phase 54-55 validation scope

Repository source validation now includes `scripts/verify_phase_54_55.py`, Reminder Reliability Center tests, Evidence Timeline tests, and the existing Phase 0-53 contract chain. Android compile, lint, and APK assembly remain Devtool target gates.


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


## UI/UX V2 source-contract extension

The Phase 37 release-candidate and target-execution baseline remains intact. The source tree now also
implements the full **Phase 38 through Phase 47 UI/UX V2 roadmap**:

- typed Today, Plan, Practice, Progress, focus, utility, and compatibility routes
- adaptive compact/expanded navigation with destination-derived selection and restored root stacks
- repository-backed Today, Plan, Practice, and Progress workspaces
- persistent focus-session duration and notes with Room schema 3 migration coverage
- global plan switching, search, quick-add, and system access
- English and pt-BR resource parity
- legacy route translation plus dedicated management/detail routes
- accessibility, source-hygiene, migration, route, and workspace contract gates

The V2 source contract is enforced by `scripts/verify_ui_ux_v2.py`, wired into the repository
contract task, and supplemented by focused route, domain, migration, and workspace tests. Plan
switching deliberately bypasses restored root-stack state so a newly selected plan cannot reopen a
nested destination belonging to the previous plan.

## UI/UX V2 post-delivery audit status

The V2 contract was tightened after an independent promise audit found that marker-oriented checks
could miss functional regressions. The current contract now fails if the source loses typed
recommendation routing, direct review execution, subject expansion, quick-add workspace context,
trend/adaptation semantics, scoped focus back handling, Room 1 to 3 / 2 to 3 migration coverage,
schema-2 backup compatibility, large-font stacking, RTL-aware padding, pane/traversal semantics, or
48 dp focusable controls.

## UI/UX V2 validation boundary

Source-level validators, deterministic Room schema verification, focused Kotlin compilation, and
clean-overlay application simulation can run in the artifact-construction environment. The final
Devtool target must still execute the configured Gradle compilation, Android resource, lint, unit,
instrumentation/migration, assembly, install/upgrade, predictive-back, edge-to-edge, locale, font
scaling, TalkBack, switch-access, and real-device acceptance gates. Those checks are not pre-marked
as passed in this repository report.

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


## Phase 48-49 source validation

The follow-on readiness/adaptive-reschedule overlay adds `scripts/verify_phase_48_49.py`. Source validation in this environment covered the UI/UX V2 verifier, Phase 48-49 verifier, full-app final readiness verifier, and Debug Workbench D8 verifier. Android SDK/AGP target validation remains a Devtool host gate.

## R4 Devtool test-contract hotfix

- Fixed `ExamReadinessEngine.timeFeasibility` so an empty pending schedule is treated as "no explicit backlog" rather than fabricating uncovered work from coverage alone.
- This keeps a well-covered, high-retention plan with strong mock/practice evidence in `ON_TRACK`, matching the domain test and the promised readiness semantics.
- The pessimistic branch still applies when there are real pending planned minutes, and same-day exams still use coverage directly.

## Phase 48-49 r5 resource validation hotfix

Devtool r4 reached `:feature:workspace:compileDebugKotlin` and found that `ProgressWorkspaceScreen` referenced `R.string.open_plan` without a matching workspace resource. R5 adds the English and pt-BR string resources and extends `scripts/verify_phase_48_49.py` to fail on missing Progress workspace `R.string.*` references before Android compilation.

## Phase 50-51 source validation

The weakness-drill and question-import overlay adds `scripts/verify_phase_50_51.py`. Source validation in this environment covered the UI/UX V2 verifier, Phase 48-49 verifier, Phase 50-51 verifier, full-app final readiness verifier, and Debug Workbench D8 verifier. Android SDK/AGP compilation, lint, resource merge, APK assembly, and real-device checks remain Devtool host gates.

The Phase 50-51 contract also verifies that V2 Progress uses `Icons.AutoMirrored.Outlined.TrendingUp`, so the previous deprecation warnings should not recur.


## Phase 50-51 r2 audit hotfix

The first Phase 50-51 package was audited against its promises before Devtool target execution. R2 fixes illegal Kotlin regex escapes, literal tab characters in the question importer, recurrence-only scoring that could include unrelated drill questions, optionless TSV/Anki import defaulting, and rejected-count math. `scripts/verify_phase_50_51.py` now checks those failure classes directly.

## Phase 50-51 r3 Kotlin compile hotfix

The workspace review-title formatter now copies nullable `ReviewItem` identifiers into local values before null checks. This is required because the identifiers are public properties declared in `core:domain`, while the formatter compiles in `feature:workspace`; Kotlin cannot smart-cast repeated reads of those cross-module properties. The Phase 50-51 verifier rejects the former direct-property pattern and requires all four stable local snapshots.

## Phase 52-53 validation

Source validators cover Mock Exam Studio contracts, workspace wiring, widget action routing, localized resources, and repository hygiene. Android compilation and instrumentation remain Devtool target gates.

Phase 52/53 r2 source validation passed with disputed-question filtering, dynamic mock preview, and widget action contracts.

## Phase 52-53 r4 Kotlin compile hotfix

Devtool r3 reached `:core:domain:compileKotlin` and failed because `MockExamStudioPlanner` referenced the private extension function as `Question::isExamEligible`. R4 uses an explicit lambda and extends `scripts/verify_phase_52_53.py` to reject that unsafe function-reference pattern. Source validators pass in this environment; Android compile/assemble remains the Devtool target gate.


## Phase 54/55 r3 resource and lint hotfix

- Fixed reminder reliability strings that failed Android resource flattening.
- Added validation guards for risky reminder string escapes and widget `android:tint` lint regressions.
