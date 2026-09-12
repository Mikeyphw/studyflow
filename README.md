## ATT07 — Cross-Device Delivery Router

StudyFlow now has a shared ATT07 delivery router that chooses one canonical owner and bounded mirror targets for every Attention occurrence across Android, Desktop, Web/PWA, Android TV, and Web TV. Routing is ledger-first, capability-aware, foreground-truthful for TV clients, and exposes canonical move-to-device handoff requests. See `ATT07_CROSS_DEVICE_DELIVERY_ROUTER.md`.

## ATT04 — Desktop Native Attention + Tray

Desktop now has a production attention runtime backed by the shared planner and durable ATT01 ledger. It performs startup recovery, native capability-aware tray notifications, snooze/dismiss lifecycle, a live tray mini-dashboard, and truthful settings/diagnostics while StudyFlow is running. See `ATT04_DESKTOP_NATIVE_ATTENTION_TRAY.md` and `docs/product/DESKTOP_ATTENTION_RUNTIME.md`.

# StudyFlow Android

## ATT03 — Android Widget Suite 2.0

Android now has a configurable five-mode StudyFlow widget suite: Today, Next, Reviews, Session, and Compact. Each instance can follow the default plan or pin its own active plan; active-session controls and Start-next are real domain actions. Widget freshness is independently scheduled from notification permission using a dedicated inexact AlarmManager path, with boot/time/timezone/date/package recovery and Debug Workbench evidence. See `ATT03_ANDROID_WIDGET_SUITE_2_0.md` and `docs/product/ANDROID_WIDGET_SUITE.md`.


## Current status: SFLP24 sealed; REL01 target release validation runner added

SFLP24 completed the cross-platform StudyFlow Link Protocol source seal. REL01 adds the target-machine
release validation runner that turns the existing signed APK/AAB, connected Android, restore/restart,
and manual device-evidence gates into one fail-closed operational workflow. REL01 is not Phase 65 and
it does not claim release readiness without real target evidence.

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
- CSOV03 adds the provider abstraction used by the later optional provider implementations and catalog lanes.
- Phase 34 replaces passive-but-clickable chips with non-clickable status pills.
- Phase 35 refreshes README, changelog, validation notes, and artifact hygiene contracts.
- Phase 36 sets the release-candidate identity, packaging policy, and signing boundary.
- Phase 37 adds the real-device acceptance checklist and report template without claiming target evidence from source validation.

The app now uses a release-candidate version name, `1.0.0-rc1`, while keeping target-machine gates
explicitly evidence-based. Source validators can prove repository contracts; Devtool or an Android
build host must still produce build, install, upgrade, and device-smoke evidence.


## PARITY04 — Five-client full-mesh acceptance

PARITY04 closes cross-platform product parity across Android, Desktop, Web, Android TV, and Web TV. The seal runs all ten direct pair combinations, simultaneous convergence, offline conflict reconciliation, exactly-once completion, activity continuation, presentation to both TV implementations, direct Desktop/Web-to-TV pairing without Android as a hub, capability rejection, loop-free routing, Presence coexistence, portable canonicalization, and all 40 WEB20 semantic vectors. `REL-FINAL` evidence execution is the next and only remaining canonical track.

## PRESENCE01 — Peer/activity presence

PRESENCE01 adds optional ephemeral presence across Android, Desktop, Web, Android TV, and Web TV. Presence has explicit online/away/offline/stale states, bounded stale/expiry windows, reconnect-session supersession, simultaneous client instances, capability-filtered resumable activity hints, and route-trace loop prevention. It runs after authenticated durable reconciliation and failures are non-fatal, so presence is never a prerequisite for sync correctness.

## ATT02 Android Attention System 2.0

Android now implements the ATT02 reference attention client on top of ATT01: six independent reminder categories, explicit timing and quiet-hour policy, four Android notification channels, actionable notifications, session/exam reminders, reboot/time/time-zone/package/app-resume recovery, live domain invalidation, exact-target deep links, a reliability center, and durable Attention-ledger integration. See `ATT02_ANDROID_ATTENTION_SYSTEM_2_0.md` and `docs/product/ANDROID_ATTENTION_SYSTEM.md`. ATT03 owns the expanded Android widget suite.

## Product boundary

Current cross-platform capability truth is maintained in `docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md`.

StudyFlow is **local-first with optional sync**. A fresh install works fully offline, requires no StudyFlow account, and leaves sync off until the user explicitly chooses a provider. CSOV05 implements real manual SAF-folder checkpoint transfer; CSOV07-CSOV09 add conservative merge/conflict/background foundations; CSOV10 implements the optional WebDAV / Nextcloud provider lane. Encrypted `.sfbackup` bundles remain the remote-transfer boundary. Google Drive remains an optional provider/catalog shell rather than a claimed equivalent I/O implementation.

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

Mobile debug APK:

```bash
devtool -r /path/to/studyflow --target studyflow_android build
```

Android TV debug APK:

```bash
devtool -r /path/to/studyflow --target studyflow_tv build
```

Both Android targets use Devtool's Android build wrapper on native Termux; the TV target must not be downgraded to the generic Gradle runner.

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

Android TV is a size-constrained separate APK track, implemented through TVOV26. `studyflow_tv` is a
focused Devtool target that compiles/tests/packages `:app-tv` without pulling the Android phone feature
fanout into the TV APK:

```bash
devtool -r /path/to/studyflow --target studyflow_tv validate
```

The TV release APK budget is 25 MiB target / 35 MiB hard ceiling. TVOV22-TVOV26 provide explicit local
pairing, typed presentation projection, Android phone control, real cast-state synchronization and
domain persistence convergence. That TV transport remains Android-phone/TV-specialized until SFLP11,
but SFLP08/SFLP09 now give Android and Desktop a separate first-class generic SFLP peer link with direct
pairing, trust, persistent offline queues and authenticated reconciliation. Android is therefore no
longer the required hub for Android↔Desktop connectivity; Web and TV generalization remain later SFLP work.

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

TVOV01 introduced the real `:app-tv` module with Compose compiler disabled for a small Android TV APK.
Later TVOV overlays now provide the full native TV navigation, study, pairing, control, persistence, and
physical-acceptance track while preserving the original size-first dependency boundary.


### Android TV TVOV02

TVOV02 keeps the TV APK dependency budget explicit before feature wiring. The `:app-tv` manifest now declares backup/data-extraction rules, avoids fixed-orientation and minSdk-guarded resizeable attributes, and uses a compact banner resource so TV lint can run without source warnings.


### Android TV TVOV03

TVOV03 adds the shared phone-to-TV control protocol in `:core:tvcontrol`. The Android mobile app and the TV app now share the same protocol module for revocable pairing, remote-control commands, and castable presentation state. The implementation remains size-first: no Cast SDK, MediaRouter, AndroidX TV UI, Compose Runtime, phone feature-module fanout, `core:data`, or `core:database` is added to `:app-tv`.


### Android TV TVOV04

TVOV04 adds native D-pad/focus primitives to the size-first Android TV app. The focus policy is pure Kotlin, maps Android remote key codes to small commands, provides deterministic initial focus and non-wrapping directional movement, and gives the placeholder TV shell a visible focusable action row. The TV APK still avoids Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature-module fanout, `core:data`, and `core:database`.


### Android TV TVOV05

TVOV05 adds the structured Android TV navigation shell. `:app-tv` now has a native D-pad-aware
navigation rail backed by a small destination enum and mapped to the shared `:core:tvcontrol`
surfaces, so paired-phone control and local TV navigation share the same route contract. The TV
APK still avoids Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules,
`core:data`, and `core:database`.


### Android TV TVOV06

TVOV06 adds the structured native Android TV Home surface. The TV app now has a real default entry point with remote-first cards for Today, Practice, Reviews, and Mock Exam, plus a paired-phone entry path that stays on the shared TV control protocol. TVOV06 also removes the unused TVOV05 string resources reported by lint.

### Android TV TVOV07

TVOV07 adds the Android TV Today workspace as a structured TV surface. It renders a real Today cockpit with next study block, due reviews, practice queue, weekly progress, and schedule/mock-prep context. Today actions are D-pad focusable and also produce shared paired-phone control commands.

### Android TV TVOV08

TVOV08 adds the Android TV Study session experience. It introduces a dedicated Study Session destination, a ready/running/paused/completed state machine, TV remote and paired-phone start/pause/resume/next/complete actions, Play/Pause remote-key handling, and `StudyFlowTvCastState` output for session casting.



### Android TV TVOV09/TVOV10

TVOV09 and TVOV10 add the Android TV Question Practice and Mock Exam Studio surfaces as one
structured overlay. Both surfaces have real TV models, native shell rendering, D-pad focus contracts,
paired-phone command routing, cast-state generation, unit tests, and verifier tasks while preserving
the separate TV APK dependency and size budget.


### Android TV TVOV11/TVOV12

TVOV11 and TVOV12 add the Android TV Review/Error Notebook and Progress/Analytics surfaces as one structured overlay. Reviews now have a source model, due queue, confidence/reason metadata, error-note state, native rendering, D-pad focus, paired-phone command routing, cast-state output, tests, and verifier. Progress now has weekly progress, accuracy, question-volume, readiness, weak-subject summaries, recommended next actions, native rendering, D-pad focus, paired-phone control routing, cast-state output, tests, and verifier while preserving the separate TV APK dependency and size budget.


### Android TV TVOV13/TVOV14

TVOV13 and TVOV14 add the Android TV read-only Schedule and limited Library/reference material surfaces as one structured overlay. Schedule now has today/week context, review windows, study blocks, practice entries, mock-exam rehearsal, D-pad focus, paired-phone navigation, and cast-state generation while keeping schedule mutation outside `:app-tv`. Library now has curated reference cards, source labels, short TV-readable excerpts, related practice/review navigation, D-pad focus, paired-phone command routing, and Library cast-state output while keeping file management, SAF, Room, and repositories outside the TV APK.


### Android TV roadmap and working rules

`TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md` carries the full TVOV00-TVOV26 roadmap and the
standing implementation preferences for this track. TV overlays must be structured implementation
units with source, shell rendering, control/cast integration where relevant, tests, verifiers,
Devtool `studyflow_tv` wiring, docs, and commit-enabled overlay metadata. Light placeholder overlays
are not acceptable for this roadmap.


### Android TV TVOV15/TVOV16

TVOV15 adds a native TV-safe Settings surface for pairing status/revocation, focus-visibility
preference representation, local-first privacy, and safe About/diagnostics without phone-only
storage or account dependencies. TVOV16 adds deterministic cross-device control/state continuity:
local remote and paired-phone intents reduce into the same TV presentation state with active-item,
playback, pairing-expiry, and monotonic revision fields. TVOV16 established reducer continuity before transport; TVOV22 now provides the local wire.

### Android TV TVOV17/TVOV18

TVOV17 and TVOV18 seal the automated accessibility/10-foot and optimized-Release boundaries for the separate TV APK. The native TV shell now centralizes readable typography and safe-area metrics, exposes semantic headings and polite status updates, scrolls focused content instead of clipping dense surfaces, and makes the Settings high-visibility-focus preference change the rendered focus ring. Release validation now uses R8 with optimized default rules plus resource shrinking, requires R8 mapping evidence, verifies the Release APK as a ZIP, and enforces the 25 MiB target without adding Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database`. TVOV19/20 still own manifest/package hardening and real-device/release acceptance.

### Android TV TVOV19/TVOV20

TVOV19 and TVOV20 finish the planned TV package/device acceptance sequence. TVOV19 hardens the Leanback manifest, stable `com.mikeyphw.studyflow.tv` package/version identity, banner/icon/backup rules, conditional `STUDYFLOW_RELEASE_*` signing, packaged APK inspection, and strict `studyflow_tv` task isolation so phone `:app` and feature-module work cannot leak into the TV target. TVOV20 adds a final optimized Release gate plus an optional connected-TV acceptance path for Leanback detection, install/upgrade, launch, D-pad smoke, process survival, and installed version verification. No-device validation is a clean skip and is never described as physical-device acceptance. TVOV20 originally retained the TVOV16 transport deferral; TVOV22 now adds the explicit local wire while leaving phone controller UX and live cast synchronization to TVOV23/24.

### Android TV TVOV21 typed presentation projection

TVOV21 adds a typed presentation schema in `:core:tvcontrol`, a production phone-side projector/source backed by the canonical StudyFlow repositories, and a TV-side adapter/inbox. The TV runtime no longer treats historical demo defaults as user data: until a real typed presentation snapshot arrives it shows an explicit waiting/empty state. TVOV21 intentionally left pairing and transport to TVOV22.



### Android TV TVOV22 local pairing/discovery/transport

TVOV22 closes the wire-transport gap with a small shared `:core:tvlink` Android module. The TV advertises an `_studyflow-tv._tcp.` NSD receiver only while its activity is active, creates a short-lived random six-digit pairing code plus nonce, and accepts a bounded framed local TCP protocol. Successful pairing yields an expiring revocable session credential stored only in private non-backed-up app storage. Authenticated phone commands enter the same TVOV16 reducer and typed snapshots enter the same TVOV21 presentation inbox. The phone app owns the production discovery/client objects, and TVOV23 now exposes them through the actual Settings-accessible phone controller UI. No Cast SDK, MediaRouter, Room, TV repository access, or background discovery was added.


### Android TV TVOV23 phone controller

TVOV23 adds the real Android phone controller on top of TVOV22 instead of introducing another transport. Settings opens an app-owned controller screen that discovers nearby StudyFlow TVs, selects a receiver, checks the TV pairing window, accepts the six-digit TV code, resumes an unexpired saved session, refreshes continuity, navigates every TV destination, and sends Study Session, Question, Mock Exam, Review, and Back commands through the shared control protocol. Remote revoke and local forget are explicit. `feature:settings` stays transport-agnostic, discovery remains foreground-only, and live typed-presentation synchronization remains the TVOV24 boundary. The TV package identity advances to version code 23 / `0.1.0-tv23`.

### Android TV TVOV24 — real cast/state synchronization

TVOV24 connects the TVOV21 canonical presentation feed to the paired TV over the existing TVOV22 local link. The phone controller now offers explicit Start cast, Resync now, and Stop cast actions; snapshots are serialized toward the latest repository state, the TV acknowledges the exact retained snapshot id/time, and receiver rediscovery triggers resync. STOP_CAST clears the TV inbox to the truthful waiting state and remains pending for retry when requested while the paired TV is temporarily unavailable. No Cast SDK, MediaRouter, raw database payloads, or second transport stack are added. Domain-action persistence convergence remains TVOV25. The TV package is version code 24 / `0.1.0-tv24`.



### Android TV TVOV25 — domain truth and persistence convergence

TVOV25 makes StudyFlow persistence authoritative for TV interactions. Persistent TV/phone actions are mapped to canonical domain-action requests, executed on the phone through the existing StudyFlow session/practice/review managers and repositories, journaled for idempotent retry, and acknowledged over TVOV22. TV-local actions use a bounded outbox; phone-controller actions use the same executor directly. The resulting repository state is re-projected through TVOV21 and synchronized through TVOV24. The TV no longer fabricates mock scores, elapsed time, answers, review evidence, or lifecycle completion. The TV APK remains free of Room/data repositories and advances to version code 25 / `0.1.0-tv25`. TVOV26 is the remaining real two-device final seal.


### Android TV TVOV26 final physical seal

TVOV26 completes the Android TV implementation chain. It adds fail-closed two-device acceptance for a real Android phone plus Leanback TV, exact signed Release artifact installation, fresh pairing, authenticated control, LIVE canonical cast acknowledgement, real domain persistence, restart/resync, STOP_CAST, and revoke. Phone cast intent now survives process restart only when the user had explicitly started casting and the same default plan/pairing remains valid. Source validation cannot claim physical acceptance; `verifyStudyFlowTvFinalProductSeal` requires a PASS evidence report from the real-device runner.

Pending STOP_CAST intent is also persisted until acknowledgment so a phone-process restart cannot resurrect a cast the user already stopped.


## SFLP10/SFLP11 — Web + Android TV generic peers

Web now boots a persistent local SFLP peer and Android TV now joins the same generic identity/trust/topology/sync stack while keeping the legacy TV remote-control adapter for compatibility. Android is not a required hub for SFLP peer sync.


### SFLP12/SFLP13 Web TV handoff

StudyFlow Link now supports Web TV receiver connectivity and cross-device activity handoff. Web TV
uses `studyflow.sflp.web_tv.receiver.v1` receiver offers, while all clients use typed
`activity.handoff_*` messages for resume/projection without making Android a required hub.

### SFLP14/SFLP15 live continuity and projection

StudyFlow Link now includes live session continuity and universal presentation projection. Sessions carry canonical
fencing, epochs, ordered events and exactly-once completion through the operation journal. Projection is source/sink
based across Android, Desktop, Web, Android TV and Web TV with monotonic frames and receiver acknowledgements.


## SFLP16 + SFLP17 Control/Input Delegation

- Added leased generic control protocol for peer-to-peer StudyFlow control.
- Added target-gated input delegation for keyboard, text, D-pad, select/back and touch input.
- Added Android, Desktop, Android TV and Web bridges plus schema, golden vectors and verifier wiring.

### ATT01 unified attention foundation

StudyFlow now has a shared durable attention domain and ledger for reminder identity, lifecycle, dedupe, source
sequence tracking, quiet-hour/timing metadata, actions, and delivery policy. SFLP18 schedule operations retain the
legacy compatibility marker while carrying a lossless `attentionScheduleChange`. Android migrates existing reminder
bookkeeping into the ledger; Desktop, Web, and Android TV have persistence adapters. This foundation does not
claim that every client already exposes a native reminder surface; ATT02-ATT06 provide those product experiences.

### SFLP18/SFLP19 notification, schedule, and material handoff

StudyFlow peers can now exchange targeted notification deliveries, schedule reminder changes, and
read-only material stream handoffs. TV surfaces remain presentation/read sinks only; material streams
that require `library.import` or workspace writes are rejected before chunk transfer.

### SFLP20/SFLP21 clipboard/share/import and conflict review

StudyFlow peers can exchange clipboard/share/import offers and conflict-review decisions through SFLP.
Phones, desktop, and web can apply imports when they advertise write/import capabilities. TV surfaces
can preview incoming shared content and conflict summaries, but they reject write/import authority.

### SFLP22/SFLP23 account backup and release validation

StudyFlow peers can now exchange account-recovery and backup-restore handoff metadata through SFLP
without moving plaintext secrets through the mesh. The final release gate is consolidated as
`verifySflp22Sflp23AccountBackupValidation`, while intermediate overlays remain intended for
`--no-validate` application.

### SFLP24 final release validation seal

SFLP24 closes the cross-platform SFLP lane with `verifySflp24FinalReleaseSeal`. The task depends on
SFLP22/SFLP23 plus the core, Android, Android TV, and Desktop test lanes, and the final protocol
golden vector is shared between `protocol/sflp/v1/golden` and `:core:link` test resources.

## REL02 release evidence exporter

REL02 adds `scripts/export_release_evidence_report.py`, a post-SFLP release evidence exporter and failure triage bundle generator. It consumes the REL01 evidence directory, writes `release-evidence-summary.json`, `release-failure-triage.json`, `release-evidence-report.md`, and `studyflow-release-evidence-bundle.zip`, and remains fail-closed when evidence is `PENDING_TARGET_EXECUTION` or `BLOCKED`.

## REL03/REL04 artifact identity and rollout manifest

REL03 signed artifact identity records exact APK/AAB SHA-256, structure checks, target-host signature-tool results, and install-smoke evidence without storing signing secrets. REL04 rollout manifest reads REL01, REL02 and REL03 reports and writes `release-channel-manifest.json` plus `rollout-readiness.json`. Any missing, pending or blocked report keeps the release fail-closed.

### REL05 release notes exporter / REL06 rollback package manifest

REL05 adds `scripts/export_release_notes.py` to turn changelog evidence into `release-notes.json`, `release-notes.md`, and `release-notes-play-store-draft.txt` without manufacturing release readiness. REL06 adds `scripts/build_rollback_package_manifest.py` to produce `release-rollback-manifest.json`, `rollback-package-manifest.md`, `rollback-command-template.sh`, and `studyflow-rollback-package-bundle.zip` from REL01-REL05 evidence. Both gates fail closed with `PENDING_TARGET_EXECUTION` or `BLOCKED` and redact credential-looking values.

### REL07 release checklist gate / REL08 final release-ops seal

REL07 adds `scripts/check_release_operator_checklist.py` to aggregate REL01 through REL06 target evidence and operator checklist state into `release-checklist-gate.json`, `release-checklist-gate.md`, and `release-checklist-command-template.sh`. REL08 adds `scripts/build_release_ops_final_seal.py` to emit `release-ops-final-seal.json`, `release-ops-final-seal.md`, and `studyflow-release-ops-final-bundle.zip`. The final seal is evidence only: it does not upload, publish, install, sign, delete, or roll back anything, and it only sets `releaseOpsReady=true` when all prior evidence and the REL07 checklist pass.

### TV control and cast

A paired phone can **control every TV destination** through the shared TV control protocol and **cast study sessions**
using StudyFlow's typed presentation/cast state rather than screen mirroring.

TVOV01 adds a real `:app-tv` module; later TV overlays retain its separate-package and dependency-budget contract.

## WEB20 shared semantic golden vectors

WEB20 adds 40 language-neutral golden vectors in `protocol/semantic/v1/golden-vectors.json` and executes them through Kotlin/core, TypeScript/Web, and Desktop production semantics. The contract covers readiness, schedule generation/rescheduling, scoring, review/SRS, study time, goals, Error Notebook state, canonical portable JSON, and deterministic SFLP conflict resolution. PARITY04 reuses this exact fixture.

## REL-FINAL roadmap audit and release evidence campaign

REL-FINAL inventories and re-runs every pre-existing roadmap/product verifier, records the imported connectivity/release promise ledger, and orchestrates the existing REL01-REL08 evidence tooling. Static audit success never synthesizes release readiness: signed artifact, connected-device, restart/restore, device-observation, and operator-checklist evidence remain mandatory.
The exhaustive legacy-verifier pass is isolated in artifact validation before Gradle starts; the Gradle terminal seal intentionally avoids rerunning recursive historical aggregate verifiers under parallel compilation load.

## REL-FINAL Devtool release runner

The Android release evidence campaign uses the dedicated `studyflow_release` Devtool Android target. The target runs `verifyFinalReleaseAutomatedGate` through Devtool's Termux-safe Android builder with Termux AAPT2, NDK 29, configuration cache disabled, and the unrelated repository-wide `verifyFormatting` backlog excluded from this evidence lane. The release shell script never invokes raw Gradle.

`studyflow_tv` remains an Android Devtool target, so normal TV builds continue using the same Termux-safe AAPT2/toolchain routing as the mobile app.

### ATT05 Web/PWA attention runtime

Normal Web/PWA clients now expose a Notification Center and participate in the ATT01 Attention ledger. The Web runtime schedules foreground reminders, can request browser notification permission from an explicit user action, handles service-worker notification clicks/closes and push deliveries, supports optional Web Push subscription setup, and uses browser badges as a progressive enhancement. Without Push, Web background delivery remains foreground-only by design.
