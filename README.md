# StudyFlow Android

StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study. It keeps the study plan,
content tree, schedule, sessions, practice, reviews, reminders, library resources, progress,
privacy controls, and local checkpoint status on the device.

## Current status

This source tree is implemented through **Phase 51**, including the complete UI/UX V2 workspace migration:

- Phases 38-47 replace the feature-catalog shell with Today, Plan, Practice, and Progress workspaces.
- The adaptive shell preserves root back stacks and legacy route compatibility.
- Focus mode persists target duration through Room schema 3 and restores active sessions.
- Global search, plan switching, quick add, settings, reminders, backup, privacy, and diagnostics remain reachable.

- Phases 48-49 add explainable exam readiness and preview-first adaptive rescheduling.
- Phases 50-51 add targeted weakness drills and a CSV/TSV/Markdown question import pipeline.
- Phase 50-51 r2 hardens importer regexes, TSV/Anki short-answer defaults, recurrence-safe drill ranking, and audit tests.

The release-candidate identity and acceptance baseline was implemented through **Phase 37**; the UI/UX V2 migration then advances the source tree through Phase 51.

The preceding release-identity and acceptance framework remains active:

- Phase 30 removed normal-user release checklist scaffolding from Settings.
- Phase 31 wired the dashboard primary action to the Study Sessions flow.
- Phase 32 moved diagnostics behind a developer-only debug gate.
- Phase 33 completed the former sync surface as an offline-first local checkpoint.
- Phase 34 replaces passive-but-clickable chips with non-clickable status pills.
- Phase 35 refreshes README, changelog, validation notes, and artifact hygiene contracts.
- Phase 36 sets the release-candidate identity, packaging policy, and signing boundary.
- Phase 37 adds the real-device acceptance checklist and report template without claiming target evidence from source validation.

The app now uses a release-candidate version name, `1.0.0-rc1`, while keeping target-machine gates
explicitly evidence-based. Source validators can prove repository contracts; Devtool or an Android
build host must still produce build, install, upgrade, and device-smoke evidence.

## Product boundary

StudyFlow is currently an **offline-first** app. It does not require an account and does not present
a cloud provider flow. Local checkpoint status means the app can summarize whether local backup and
restore decisions are ready for the selected Study Plan; it is not an online synchronization service.

A `StudyPlan` is the complete and independent workspace for one study objective. Subjects, nested
topics, schedules, sessions, questions, mock exams, reviews, notes, videos, annexes, analytics, and
local checkpoint evidence are scoped through `studyPlanId`.

## Toolchain

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Kotlin 2.4.10
- JDK 17
- Android SDK 37
- Android SDK Build Tools 36.0.0
- Compose BOM 2026.06.00

AGP 9 built-in Kotlin and the new Android DSL are used for Android modules. Do not apply
`org.jetbrains.kotlin.android`, and do not set `android.builtInKotlin=false`. KSP, Compose, and
Serialization are owned by the included convention build to avoid split plugin classloaders.

The repository includes a pinned `gradle/wrapper/gradle-wrapper.properties`. Devtool can provision
and select the matching managed Gradle distribution when a checked-in `gradlew` launcher is absent.

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
feature:settings/       Language, appearance, local checkpoint, backup, privacy, diagnostics entry
docs/                   Architecture, product, quality, roadmap, and release notes
scripts/                Source-contract validation entry points
```

## Build with Devtool

```bash
devtool -r /path/to/studyflow --target studyflow_android build
```

Apply and validate overlays with explicit target selection:

```bash
devtool --yes --copy -r /path/to/studyflow --target studyflow_android apply overlay   --validate   --task :app:compileDebugKotlin   --task :app:lintDebug   --task :app:assembleDebug   --commit "Apply StudyFlow overlay"   /path/to/overlay.zip
```

## Validate source contracts

```bash
./scripts/validate_repo.sh
```

Without an Android SDK, the Python source-contract validators still run. With an Android SDK and a
compatible Gradle launcher, `validate_repo.sh` also runs the Gradle verification chain. Devtool on
the Android build host remains the source of truth for target-machine gates such as compile,
resource merge, lint, assemble, package signing, install, upgrade, and device-smoke evidence.

Useful focused checks:

```bash
python3 scripts/verify_phase_36.py
python3 scripts/verify_phase_37.py
python3 scripts/verify_full_app_final_user_readiness.py
gradle verifyRepositoryContracts
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
