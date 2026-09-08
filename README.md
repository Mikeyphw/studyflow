# StudyFlow Android

## Current status: OV20 public-exam promise audit and integrated validation seal implemented

The post-CPOV specialization is complete through OV20: the BB pre-edital preset, Redação workspace,
reference mock blueprint, typed activity deep-links, phase planning, reference freshness, adaptive
question quotas, readable calendar schedule, correctness seal, literal promise audit, and final
integrated validation layer all reuse the existing Study Plan graph. See
`docs/roadmap/OV01_OV20_PUBLIC_EXAM_COMPLETION_MATRIX.md`.

The earlier **CPOV13 public-exam integration seal implemented** status remains valid as the
architecture/persistence foundation underneath this specialization; OV20 extends its validation
coverage rather than replacing that graph.

StudyFlow includes the completed remediation roadmap through OV-13 and the Concurso Público
specialization through CPOV13. Source/static contracts do not claim product sealing: target-machine
Debug/Release validation, signed-artifact evidence, and the existing explicit manual device
observations remain separate fail-closed release evidence.

The CPOV sequence adds edital revisions, persistent study cycles, explainable priority, banca-aware
questions and mocks, Error Notebook 2.0, structured legislation, a derived concurso dashboard,
final-stretch strategy, and public-exam onboarding while preserving one Study Plan graph. See
`docs/roadmap/CPOV_PUBLIC_EXAM_COMPLETION_MATRIX.md`.


StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study. It keeps the study plan,
content tree, schedule, sessions, practice, reviews, reminders, library resources, progress,
privacy controls, and local checkpoint status on the device.

## Historical implementation baseline

The source tree includes the complete UI/UX V2 workspace migration and later remediation overlays.
The historical Phase 48-64 features include:

- Phases 38-47 replace the feature-catalog shell with Today, Plan, Practice, and Progress workspaces.
- The adaptive shell preserves root back stacks and legacy route compatibility.
- Focus mode persists target duration through Room schema 3 and restores active sessions.
- Global search, plan switching, quick add, settings, reminders, backup, privacy, and diagnostics remain reachable.

- Phases 48-49 add explainable exam readiness and preview-first adaptive rescheduling.
- Phases 50-51 add targeted weakness drills and a CSV/TSV/Markdown question import pipeline.
- Phase 50-51 r2 hardens importer regexes, TSV/Anki short-answer defaults, recurrence-safe drill ranking, and audit tests.
- Phase 62 adds local preview-first onboarding and sample-plan provisioning.
- Phase 63 adds an exam-date-aware, evidence-derived runbook without a new persistence source.
- Phase 64 adds a version-bound seven-day sprint preview over existing unlocked planned activities.
- The post-Phase-64 final refresh binds runbook/sprint behavior into the Workspace connected suite and the signed Release restore/restart journey.

The release-candidate identity and acceptance baseline was implemented through **Phase 37**; the UI/UX V2 and post-V2 tracks advance the source tree through Phase 64.

The preceding release-identity and acceptance framework remains active:

- Phase 30 removed normal-user release checklist scaffolding from Settings.
- Phase 31 wired the dashboard primary action to the Study Sessions flow.
- Phase 32 moved diagnostics behind a developer-only debug gate.
- Phase 33 completed the former sync surface as an offline-first local checkpoint.
- CSOV00-CSOV01 migrate the product language to local-first optional cloud sync and add an off-by-default sync settings model.
- CSOV02 adds the encrypted `.sfbackup` sync bundle format with manifest, checkpoint, database snapshot, attachments, and integrity entries.
- CSOV03 adds the provider abstraction that future SAF-folder, WebDAV, Google Drive, Dropbox, and OneDrive implementations will use.
- Phase 34 replaces passive-but-clickable chips with non-clickable status pills.
- Phase 35 refreshes README, changelog, validation notes, and artifact hygiene contracts.
- Phase 36 sets the release-candidate identity, packaging policy, and signing boundary.
- Phase 37 adds the real-device acceptance checklist and report template without claiming target evidence from source validation.

The app now uses a release-candidate version name, `1.0.0-rc1`, while keeping target-machine gates
explicitly evidence-based. Source validators can prove repository contracts; Devtool or an Android
build host must still produce build, install, upgrade, and device-smoke evidence.

## Product boundary

StudyFlow is now **local-first with optional cloud sync**. A fresh install works fully offline, requires no StudyFlow account, and leaves cloud sync off until the user explicitly chooses a provider. Local checkpoint status still summarizes whether backup and restore decisions are ready for the selected Study Plan; provider-backed upload, download, and merge flows are staged after the settings foundation. CSOV02 defines the portable encrypted `.sfbackup` checkpoint bundle future providers will transfer. CSOV03 defines the provider API without enabling any provider by default.

A `StudyPlan` is the complete and independent workspace for one study objective. Subjects, nested
topics, schedules, sessions, questions, mock exams, reviews, notes, videos, annexes, analytics, and
local checkpoint evidence are scoped through `studyPlanId`.

## Toolchain

- Android Gradle Plugin 9.3.1
- Gradle 9.7.1 (project policy: stable >= 9.7.0)
- Kotlin 2.4.10
- KSP 2.3.11
- JDK / JVM target 21
- Android SDK 36
- Android SDK Build Tools 36.0.0
- Android NDK 29.0.14206865 (r29)
- Compose BOM 2026.06.01

AGP 9 built-in Kotlin and the new Android DSL are used for Android modules. Do not apply
`org.jetbrains.kotlin.android`, and do not set `android.builtInKotlin=false`. KSP, Compose, and
Serialization are owned by the included convention build to avoid split plugin classloaders.

The repository includes a complete Gradle wrapper (`gradlew`, `gradlew.bat`, wrapper JAR, and pinned
wrapper properties). The wrapper verifies the declared Gradle distribution SHA-256 before use.

## Repository map

```text
app/                    Application shell, navigation, app-level labs, widgets, and receivers
build-logic/            Convention Gradle plugins
core:data/              Room-backed repository and data orchestration
core:database/          Room entities, DAOs, schema, and database runtime
core:designsystem/      Theme and reusable Compose components
core:diagnostics/       Debug-only diagnostic recorder and support-bundle seal
core:domain/            Repository contracts, use cases, planners, auditors, and release gates
core:i18n/              DataStore preferences and app locale integration
core:model/             Stable domain, preference, checkpoint, and runtime-smoke models
core:navigation/        Type-safe navigation routes
core:portability/       Pure Kotlin package schema, codec, checksums, and preview models
core:testing/           Reusable fixtures and test fakes
feature:dashboard/      Legacy dashboard implementation retained for compatibility
feature:workspace/      Today, Plan, Practice, Progress, focus mode, and global workspace utilities
feature:plans/          Plan lifecycle, editor, settings, portability, and snapshots
feature:subjects/       Subject, nested-topic, syllabus import, filters, and bulk-edit UI
feature:schedule/       Schedule rules, planned activities, recovery, and execution state
feature:sessions/       Timed study sessions, pause/resume, completion, and history
feature:practice/       Question bank, practice tests, favorites, disputed items, and mocks
feature:reviews/        Spaced repetition, flashcards, wrong-answer notebook, remediation
feature:library/        Materials, videos, annex metadata, and annex copy storage
feature:progress/       Goals, notes, progress cockpit, and pinned study notes
feature:analytics/      Local insights from sessions, schedule, practice, reviews, and goals
feature:search/         Global search and local recommendations
feature:reminders/      Reminder scheduling, notification permission UI, and widget evidence
feature:privacy/        Retention, local export security, and restore dry-run explanation
feature:settings/       Language, appearance, optional sync, backup, privacy, diagnostics entry
docs/                   Architecture, product, quality, roadmap, and release notes
scripts/                Source-contract validation entry points
```

## Build with Devtool

```bash
devtool -r /path/to/studyflow --target studyflow_android build
```

Apply and validate overlays with explicit target selection:

```bash
devtool --yes --copy -r /path/to/studyflow --target studyflow_android apply overlay \
  --validate \
  --task :app:compileDebugKotlin \
  --task :app:compileReleaseKotlin \
  --task :app:lintDebug \
  --task :app:lintRelease \
  --task :app:assembleDebug \
  --commit "Apply StudyFlow overlay" \
  /path/to/overlay.zip
```

## Validate source contracts

```bash
./scripts/validate_repo.sh
```

`validate_repo.sh` is a full Android validation entry point and now fails closed when no Gradle
launcher or Android SDK is available. Source-only contract checks can still be run explicitly with
the individual `scripts/verify_*.py` validators. Devtool on the Android build host remains the source
of truth for target-machine gates such as compile, resource merge, lint, assemble, package signing,
install, upgrade, and device-smoke evidence. Ordinary `studyflow_android` validation packages the
Debug APK only; signed Release APK/AAB generation is intentionally reserved for the credentialed
final-release gate in `scripts/run_final_release_acceptance.sh`.



## Android TV roadmap

Android TV starts as a size-constrained separate APK track. `studyflow_tv` is present as a
source-contract Devtool target, but it does not compile `:app-tv` until TVOV01 creates that module:

```bash
devtool -r /path/to/studyflow --target studyflow_tv validate
```

The TV release APK budget is 25 MiB target / 35 MiB hard ceiling. TV is also a paired companion
surface: the Android mobile app must eventually control every TV destination and cast study sessions,
questions, reviews, mock exams, progress, and schedule views to the TV app through explicit,
revocable pairing.

Useful focused checks:

```bash
python3 scripts/verify_phase_36.py
python3 scripts/verify_phase_37.py
python3 scripts/verify_full_app_final_user_readiness.py
./gradlew verifyRepositoryContracts
```

## Release candidate package

StudyFlow `1.0.0-rc1` uses `versionCode = 37`. Release-candidate packaging is documented in
`docs/release/RELEASE_PACKAGING.md`. Signing keys, keystore passwords, and store credentials must
remain outside the repository and outside overlay artifacts. Release shrinking remains disabled for
this candidate until a separate R8/minification validation phase explicitly turns it on.

## Real-device acceptance

`docs/release/FINAL_ACCEPTANCE_CHECKLIST.md` and `FINAL_ACCEPTANCE_REPORT.md` define the required
fresh-install, upgrade-install, notification, widget, backup, restore dry-run, locale, theme,
background/foreground, kill/reopen, date/time, and accessibility checks. The checked-in report is a
**PENDING TARGET EXECUTION** template. It must be filled from a real device or emulator before a
release is claimed ready.

## Languages

- Code, identifiers, serialized fields, build logic, and technical documentation: English
- UI: English and Portuguese (Brazil)
- User-created content: stored exactly as entered

## Persistence, backup, and privacy

Structured Study Plan data is persisted with Room through `:core:database` and
`RoomStudyPlanRepository`. Preferences remain in DataStore. `.studyplan` packages are checksum
verified, can be inspected before import, and support dry-run restore checks that do not mutate the
local database. Exported files remain in the user-selected location and are managed outside the app.

Password encryption protects exported packages. App-private snapshots and annex copies remain below
app-private storage until the owning object is deleted. The privacy center explains retention,
recovery, and local security boundaries in normal product language.

## Developer diagnostics

Developer diagnostics are available only when the app host passes the debug-build diagnostics gate.
They expose bounded, redacted, support-oriented runtime probes and diagnostic labs. Normal Settings
must not show release checklists, phase scoreboards, or development placeholders.

## Package

`com.mikeyphw.studyflow`

The source artifact is cleaned of stale Devtool validation state; fresh local Devtool runs may create new transient state on the target machine.

## Artifact hygiene

StudyFlow overlays must not ship build outputs, Gradle caches, Python caches, NUL bytes, generated Devtool reports, or Devtool state payloads. Devtool reserves `.devtool/` runtime metadata and rejects overlay inventory entries that target it, so old local validation state is cleaned with an explicit command outside the overlay when needed.


## Phase 48-49 readiness and adaptive reschedule

StudyFlow now adds an explainable exam-readiness score and a preview-first adaptive reschedule wizard on top of UI/UX V2. The feature is repository-backed, keeps the existing toolchain, and mutates only safe existing unlocked schedule activities.

### Phase 52-53: Mock Exam Studio and widget actions

StudyFlow now includes a mock-exam studio in the V2 Practice workspace and richer Today widget actions. The studio previews eligible questions, timing, section coverage, and warnings before creating a persisted mock exam. Widget taps route through the same plan-aware launch extras as reminders.

Phase 52/53 r2 closes the Mock Exam Studio audit by exposing source/board/tag/disputed filters and dynamic previews.

### Phase 52-53 r4 compile hotfix

The Mock Exam Studio planner now uses a compile-safe explicit eligibility lambda instead of the prohibited `Question::isExamEligible` extension reference.


### Phase 54/55 r3

Resource/lint hotfix for reminder reliability strings and widget tint validation.

## Project status

- [Phase 56-57 Post-V2 Integration Release Seal](PHASE_56_57_POST_V2_INTEGRATION_RELEASE_SEAL.md)


### Phase 56-57 r2 release-readiness gate

The post-V2 seal now includes `verifyPhase56_57ReleaseReadiness` and Devtool
`app:lintRelease` validation. This is a release-facing source/lint gate, not a
claim that signed release packaging or real-device acceptance has run.


### Phase 58-59: local checkpoint queue and weekly coach

Phase 58 adds a local-first backup readiness queue to Sync Foundation. Phase 59 adds the weekly coach review to
Progress. See [Phase 58-59](PHASE_58_59_SYNC_QUEUE_WEEKLY_COACH.md) and
[Phase 58-59 acceptance](docs/quality/PHASE_58_59_ACCEPTANCE.md).


## Phase 58-59 r2 audit closure

- Scoped lint hygiene update: resource warning suppressions are now attached only to known compatibility strings instead of the entire resource file.
## Phase 60-61: smart cleanup and local exam reports

Phase 60 adds a preview-first local content-health engine to Plan and Progress. Phase 61 adds explicit
HTML/PDF exam-report export from structured readiness, coach, evidence, practice, recovery, and
content-health data. Export stays local and omits freeform notes by default. See
[Phase 60-61 implementation](PHASE_60_61_SMART_CONTENT_CLEANUP_EXAM_REPORT.md) and
[Phase 60-61 acceptance](docs/quality/PHASE_60_61_ACCEPTANCE.md).


## OV-13 final release/full-regression evidence

The final validation chain exposes `verifyFinalReleaseAutomatedGate`: mandatory external signing, signed
Release APK + AAB packaging, exact APK identity/certificate/hash verification, AAB signature-integrity verification,
session-bound connected data/database/feature/Release-app suites, and an exact-APK real-restore journey that must
survive a forced process restart. `verifyFinalReleaseVerdict` remains fail-closed until the separate manual device
observations also carry explicit evidence. Source/static validators never imply artifact or device acceptance.
Signing credentials and generated acceptance evidence remain outside source control.
- CSOV04 adds optional sync onboarding while preserving Keep offline as a first-class path.
- CSOV05 provides real manual SAF-folder sync: encrypted `.sfbackup` writes, real folder scans, authenticated restore preview, and no automatic sync.

### CSOV06 sync ledger

Optional cloud sync now records local device identity and checkpoint ledger facts while preserving local-first/offline operation.

### CSOV07 conservative merge engine

Optional cloud sync now has a stable-id merge planner that imports remote-only records, keeps local-only records, applies one-sided updates, and blocks both-sided/delete-vs-change cases for manual review.

### CSOV08 conflict review

Optional cloud sync now includes a conservative conflict review path. When the same stable-id entity changes on two devices, StudyFlow presents keep-local, use-remote, and keep-both choices instead of silently overwriting data.

### CSOV09 background sync modes

Optional sync now has explicit background modes: off, manual only, upload after changes, check daily, and full automatic. Fresh installs remain effectively off; configured sync stays manual-only by default.

### Optional cloud sync provider lane

StudyFlow remains local-first. CSOV10 adds WebDAV / Nextcloud as the first optional account-capable provider lane. Remote uploads still use encrypted `.sfbackup` checkpoints, and cloud setup is never required for offline study.

### Sync acceptance seal

The optional cloud sync roadmap is sealed with local-first defaults, encrypted remote checkpoints, conservative merge behavior, manual conflict review, and background sync gates. StudyFlow does not require cloud setup to study offline.


## CSOV12 provider setup security

- Added optional provider setup validation for SAF and WebDAV.
- Added secret-handle based diagnostics that avoid raw cloud credentials.


## CSOV13 sync runbook

- Added sync execution planner for manual upload, remote check, restore preview, and background modes.
- Restore preview stays dry-run-only and conflict-gated.

> OV20 navigation note: Mock Exam Studio deep-link launch state is encoded with the serializable `PracticeWorkspaceEntry` small enum; workspace routes do not carry Boolean UI state.


## Android TV

TVOV01 adds a real `:app-tv` module with Compose compiler disabled for `:app-tv` for a small Android TV APK. It currently boots to a minimal TV
receiver shell and deliberately avoids feature-module fanout, AndroidX TV UI, MediaRouter, and Cast
SDK dependencies until later size-budgeted overlays add pairing, focus navigation, and cast control.


### Android TV TVOV02

TVOV02 keeps the TV APK dependency budget explicit before feature wiring. The `:app-tv` manifest now declares backup/data-extraction rules, avoids fixed-orientation and minSdk-guarded resizeable attributes, and uses a lightweight banner resource so TV lint can run without source warnings.


### Android TV TVOV03

TVOV03 adds the shared phone-to-TV control protocol in `:core:tvcontrol`. The Android mobile app and the TV app now share the same protocol module for revocable pairing, remote-control commands, and castable presentation state. The implementation remains size-first: no Cast SDK, MediaRouter, AndroidX TV UI, Compose Runtime, phone feature-module fanout, `core:data`, or `core:database` is added to `:app-tv`.
