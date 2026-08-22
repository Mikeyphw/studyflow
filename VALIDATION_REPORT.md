# Phase 63-64 validation update

Source/static coverage now includes `scripts/verify_phase_63_64.py`, pure-domain tests for the exam
runbook and study sprint, bilingual workspace resource parity, version-bound sprint apply wiring,
the revised Phase 62 historical boundary that prevents runbook behavior from leaking back into
onboarding, nested ownership checks, bounded runbook priority, duplicate-Apply suppression, ordinary
Exception recovery and sprint mutation-envelope validation. No Gradle, lint, Android resource merge, instrumentation, emulator or device validation
was claimed by the intermediate Phase 63/64 overlay. The post-Phase-64 final refresh binds these
features into the Workspace connected instrumentation suite, the signed Release restore/restart journey, and the final source acceptance chain. The corrected refresh also binds Phase 63/64 journey evidence to explicit initial/post-restart instrumentation status markers rather than an unconditional recorder flag.

# Final acceptance validation update

Source validation now includes `scripts/verify_final_app_acceptance.py` and the Gradle task
`verifyFinalAppAcceptanceSeal`. The gate audits the completed V2/post-V2 contract through Phase 64 across
startup, route redirects, workspace actions, search/recommendations, readiness, reschedule, drills,
imports, mocks, widget actions, reminders, Evidence Timeline, weekly coach review, smart cleanup,
local exam report export, backup/export/import, onboarding, localization parity, warning sentries,
runbook/sprint behavior, and stack-version invariants.

PENDING TARGET EXECUTION: Android Gradle compilation, resource merging, unit execution, APK assembly,
debug/release lint, install/upgrade smoke, TalkBack, 200% font scaling, widget taps, notification
permission states, exact-alarm/battery degradation, report share-sheet behavior, and real-device
release startup remain Devtool target-machine gates.

# Phase 62 validation update

Source validation now includes `scripts/verify_phase_62.py`, focused Kotlin compilation for the new
onboarding domain blueprint, XML resource parsing, feature/plans English and pt-BR resource parity,
route/UI/app wiring checks, concrete weekly schedule-block preview checks, and clean-overlay apply simulation. The new
`verifyPhase62OnboardingSeal` task is wired into `checkAll`. Android Gradle compilation, Compose
resource merging, unit execution, assembly, and debug/release lint remain Devtool target-machine
gates and are not pre-claimed by this report.

# Phase 60-61 validation update

Source validation now includes `scripts/verify_phase_60_61.py`, focused core-domain Kotlin compilation,
Phase 60 cleanup tests, Phase 61 report-builder tests, bilingual resource/parity checks, local-export
source checks, and clean-overlay apply simulation. The new `verifyPhase60_61ContentReportSeal` task is
wired into `checkAll`. Android Gradle compilation, unit execution, assembly, and debug/release lint
remain Devtool target-machine gates and are not pre-claimed by this report.

# Phase 56-57 validation update

Phase 56 adds the Post-V2 Integration Seal. Phase 57 adds the Real-Device Readiness and UX Polish Seal.

Container validation performed here:

- `python3 scripts/verify_phase_48_49.py`
- `python3 scripts/verify_phase_50_51.py`
- `python3 scripts/verify_phase_52_53.py`
- `python3 scripts/verify_phase_54_55.py`
- `python3 scripts/verify_phase_56_57.py`
- artifact clean-apply simulation

Devtool remains responsible for Kotlin/Compose compilation, Android resource merging, lint, APK assembly, widget validation, and physical-device checks.

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


## Phase 56/57 r2 promise-closure audit

The Phase 57 release-readiness promise now has an executable source gate.
`verifyPhase56_57ReleaseReadiness` checks release identity visibility, launcher
manifest shape, widget AppCompat tint hygiene, and known reminder resource
flattening traps without requiring signing credentials. Devtool validation also
requests `:app:lintRelease` in addition to debug assemble/lint. Signed release
packaging and device-only checks remain outside this source artifact.

## Phase 58-59 validation update

Phase 58 adds a local-first backup readiness queue in Sync Foundation. Phase 59 adds a localized weekly coach review
in Progress. The overlay adds `scripts/verify_phase_58_59.py` and `verifyPhase58_59WarningHygiene` so
source hygiene is checked before Android lint.

Expected Devtool tasks include `:core:domain:test`, `:feature:settings:testDebugUnitTest`,
`:feature:workspace:testDebugUnitTest`, `:verifyPhase58_59WarningHygiene`, `:app:assembleDebug`,
`:app:lintDebug`, and `:app:lintRelease`.


## Phase 58-59 r2 audit closure

- Scoped lint hygiene update: resource warning suppressions are now attached only to known compatibility strings instead of the entire resource file.


## Phase 62 r3 hotfix validation

- Replayed against the Phase 60/61 landed baseline.
- Verified the onboarding screen no longer redeclares `formatMinuteOfDay`.
- Strengthened `scripts/verify_phase_62.py` to catch package-level helper collisions before Gradle.
## Final full-app acceptance seal r2 audit closure

- Replayed the final acceptance overlay against the reconstructed landed Phase 62 baseline.
- Found r1 source validation passed, but two promised checks were too implicit: adaptive-reschedule coverage and reminder degraded-state coverage.
- Strengthened `scripts/verify_final_app_acceptance.py` to require adaptive-reschedule preview/apply wiring, report and weekly-coach reschedule inputs, reminder blocked/degraded/action-needed/ready states, notification permission, exact-alarm availability, battery optimization, and duplicate string/plurals detection.
- Runtime app code and Android stack remain unchanged. Android compile, lint, assemble, and real-device checks remain Devtool target gates.

## OV-13 release/full-regression seal

OV-13 requires explicit PASS for every release gate; warning, failure, blocked, and missing evidence
all block `releaseReady`. The generic completed-seal path is removed and `productSealed` consumes the
evidence-backed release validation seal. Automated final validation requires signed APK + AAB packaging, exact APK/AAB identity and signature evidence,
connected Room/data/feature/app suites, and an exact-APK production-container journey with real REPLACE_EXISTING
and CREATE_AS_NEW restores followed by a forced process restart and durable re-read. Manual real-device acceptance
remains a separate evidence class and each PASS requires a non-empty observation. Generated evidence
stays under `build/reports/studyflow/` and is not pre-marked by source validation.

## CSOV02 Validation

- Static source contract: `python3 scripts/verify_csov02_sync_bundle.py`
- Unit coverage: `:core:portability:test`
- Compile gate: `:core:portability:compileKotlin`
- Purpose: validates the `.sfbackup` archive entries, manifest/checkpoint integrity, and encrypted remote bundle policy.

## CSOV03 Validation

- Static source contract: `python3 scripts/verify_csov03_provider_abstraction.py`
- Unit coverage: `:core:domain:test`
- Compile gate: `:core:domain:compileKotlin`
- Purpose: validates provider abstraction, disabled default provider, registry behavior, and encrypted `.sfbackup` transfer DTOs.
- CSOV04_OPTIONAL_SYNC_ONBOARDING: source/static contract added.
- CSOV05_MANUAL_FOLDER_SYNC: source/static contract added.

## CSOV06 sync ledger

Static contract: `verifyCsov06SyncLedger`
Recommended validation: `:core:model:test`, `:core:domain:test`, `:core:data:compileDebugKotlin`.

## CSOV07 conservative merge engine

Static contract: `verifyCsov07MergeEngine`
Recommended validation: `:core:model:test`, `:core:domain:test`, `:app:compileDebugKotlin`.

## CSOV08 validation

Static contract: `verifyCsov08ConflictReview`. Recommended runtime checks: `:core:domain:test`, `:feature:settings:compileDebugKotlin`, and `:app:compileDebugKotlin`.
