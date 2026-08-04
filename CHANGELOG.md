
## Debug Workbench D8 r4 Final Release Seal Hotfix

- Supersedes D8 r3.
- Keeps the D8 final release seal, r2 validator/JUnit fixes, and r3 DebugOverview state wiring.
- Normalizes coroutine debug thread-name suffixes in the workbench action tests so injected I/O assertions remain deterministic under Gradle's coroutine debug names.
- Hardens the D8 verifier to reject the brittle exact thread-name assertions that failed target-side validation.

# Changelog

## Phase 32-33 diagnostics de-scaffolding and offline checkpoint completion

- Renamed the debug entry to Developer diagnostics and kept it gated to debug builds.
- Replaced fallback probe/lab placeholder copy with concrete developer-support fallback states.
- Completed the former sync foundation as an offline-first local checkpoint surface that keeps the user story on this device.
- Reworked the local checkpoint model around checkpoint readiness and pending local changes instead of online transfer selection.
- Advanced release readiness and source validation through Phase 33.


## Phase 30-31 product truthfulness and dashboard action completion

- Removed normal-user Settings cards that exposed app-shell readiness, UX polish, release-candidate polish, and stale release seals.
- Renamed local-only sync surfaces to local backup checkpoints and removed user-facing online-sync promises.
- Advanced the release-readiness scope through Phase 31 plus Debug Workbench D1-D8 while keeping target-machine release gates evidence-based.
- Wired the Dashboard primary session action to the existing Study Sessions route and converted the default-plan chip into a passive badge.
- Added Phase 30 and Phase 31 verifiers plus source-level contract tests for Settings and Dashboard truthfulness.

## Debug Workbench D6 - Study engine lab

- Added aggregate-only Study Engine Lab diagnostics for schedule expansion, review queues, practice decks, mock exams, recommendations, search, progress, analytics, and sync foundation.
- Added missing-plan, cancellation, fatal-error, duplicate-kind, and bounded aggregate model tests.
- Kept the lab read-only and rejected labels, names, notes, questions, answers, and raw search/recommendation titles from diagnostics.

## Debug Workbench D5 - Database and portability lab

- Added aggregate-only Room health inspection with schema, quick-check, foreign-key, table/count, metadata, and migration-chain evidence.
- Added non-destructive portability drills through the production backup/restore drill seal.
- Added cancellation, fatal-error, missing-plan, event-pairing, migration-chain, and destructive-drill rejection tests.

## Debug Workbench D2/D3 corrective seal

- Moved timeline, clear-history, and support-bundle preparation work onto an injected I/O context.
- Added explicit unavailable and share-failed outcomes while preserving cancellation and fatal-error semantics.
- Made the typed probe registry independently testable with injected suspend operations and execution context.
- Added start/terminal event pairing for blocked probes plus cooperative and interruptible timeout coverage.
- Added real widget registration/metadata/enabled checks and manifest-linked backup-rule evidence.
- Added platform-policy, registry, and workbench-action behavior tests plus a new corrective validator.

## Debug Workbench D3 - typed runtime probes

- Added a typed probe registry with read-only platform health and bounded StudyFlow runtime checks.
- Kept reminder reconciliation and widget refresh out of the read-only suite.
- Added explicit blocked, warning, timeout and privacy-safe result contracts.

## Debug Workbench D2 — Shell

- Added a Settings-only Debug Workbench with overview, redacted timeline, probes placeholder, and reports surfaces.
- Added robust object-only JSONL decoding and recorder-health policy models with behavior tests.
- Added local copy, clear, refresh, and checksummed support-bundle sharing through a narrowly scoped cache FileProvider path.
- Preserved the no-upload, no-new-permission, no-Room-migration privacy boundary.

## Debug Workbench D1 r2: Recorder and privacy foundation seal

- Superseded the original unsealed D1 artifact after an adversarial promise audit.
- Fingerprint free-form values under generic diagnostic keys while preserving only narrowly typed machine metadata.
- Re-sanitize caller-created exception summaries at event serialization so forged model values cannot leak.
- Added collision-safe rotation filenames with a monotonic sequence and deterministic five-session retention.
- Added writer-failure fallback export with explicit flush/timeline completeness evidence.
- Expanded the behavior suite from 13 to 18 tests, including queue overflow, fixed-clock rotation collisions, forged
  exceptions, generic-key privacy, and failed-writer support export.
- Preserved Debug-only app-private recording, Release no-op behavior, fixed bundle allowlist, and no network upload.

## Phase 29: Release Candidate Polish

- Added `ReleaseCandidatePolishAuditor` for settings polish, state-screen, accessibility,
  resource-parity, trust-copy, first-run, upgrade, and roadmap-index checks.
- Added a Settings release-candidate polish card with English and Brazilian Portuguese resources.
- Added first-run/upgrade notes and a final roadmap index through Phase 29.
- Made the Phase 28 verifier phase-forward so the repository can advance past `0.28.0-phase28`.
- Advanced the release validation seal evidence through Phase 29.

## Phase 28: Backup/Restore Drill Seal

- Added `BackupRestoreDrillSeal` for non-destructive encrypted export, wrong-password,
  tamper-detection, metadata-private annex, restore dry-run, retention, and recovery-report drills.
- Made the Phase 27 verifier phase-forward so the repository can advance past `0.27.0-phase27`.
- Advanced the release validation seal evidence through Phase 28.
- Added Phase 28 tests, quality notes, and validation documentation.

## Phase 20: Wrong-Answer Notebook and Remediation Loop

- Added automatic wrong-answer capture from completed practice tests and mock exams.
- Added recurrence-aware error entries, error-review scheduling, and resolve/delete flows.
- Added wrong-answer notebook UI inside Reviews and flashcards.
- Added `WrongAnswerRemediator` for priority and remediation suggestions.
- Added DAO/repository support for finding recurring question errors and deleting resolved errors.
- Added Phase 20 verifier.

# Changelog

## Debug Workbench D6 - Study engine lab

- Added aggregate-only Study Engine Lab diagnostics for schedule expansion, review queues, practice decks, mock exams, recommendations, search, progress, analytics, and sync foundation.
- Added missing-plan, cancellation, fatal-error, duplicate-kind, and bounded aggregate model tests.
- Kept the lab read-only and rejected labels, names, notes, questions, answers, and raw search/recommendation titles from diagnostics.

## Phase 5 - Syllabus, subjects and nested topics

- Added the `:feature:subjects` module with a bilingual Study Content screen.
- Added subject and nested-topic CRUD, search, active/mastered/attention/archive filters,
  position/name/priority/progress sorting, selection, bulk edit, archive/restore, delete, and
  topic reordering.
- Added structured-text and CSV syllabus import with preview and per-plan transactional import.
- Added `StudyContentRepository`, `ManageStudyContent`, `StudyProgressCalculator`, and
  `SyllabusImportParser` in the domain layer.
- Added Room-backed `RoomStudyContentRepository` through the public data-layer factory while
  keeping `:core:database` hidden from `:app`.
- Migrated Room schema from version 1 to 2 with nullable `examStageId` columns on subjects and
  topics plus migration coverage.
- Extended Phase 4 portability normalization so older `.studyplan` packages without exam-stage
  links remain importable.
- Added derived topic, subject, and plan progress based on topic tree completion and weights.
- Added a dashboard entry point for syllabus management.
- Fixed the Phase 4 portability lint issue by replacing `LocalContext.current` resource lookups with
  `stringResource` and `pluralStringResource` while keeping `LocalContext` only for platform access.
- Added Phase 5 validation seals for syllabus structure, migration wiring, Compose resource lookup
  safety, scoped modifier imports, and data-layer boundaries.

## Phase 4 - Portable Study Plans and warning seal

- Added the pure Kotlin `:core:portability` module and versioned `.studyplan` ZIP format.
- Added SHA-256 manifest and payload verification, bounded archive reading, path traversal defense,
  duplicate-entry rejection, exact table-inventory validation, and schema consistency checks.
- Added full, privacy-stripped, structure-only, and reusable-template exports.
- Added import preview plus create-as-new, replace-existing, and compatible-merge strategies.
- Added transactional Room graph import with complete primary- and foreign-key remapping.
- Preserved all existing snapshot metadata when replacing a plan and added automatic pre-import and
  pre-restore safety snapshots.
- Added manual snapshot creation, restore, deletion, and app-private package storage.
- Added Storage Access Framework import/export UI and plan-list/dashboard integration.
- Sanitized annex references and explicitly deferred physical annex payloads to Phase 9.
- Cleared all 15 Phase 3 warnings: AutoMirrored icons, modifier parameter ordering,
  `mutableIntStateOf`, and Portuguese `many` plural quantities.

## Phase 3 r3 - Compose scoped-modifier import hotfix

- Removed the explicit `androidx.compose.foundation.layout.align` import from `PlanListScreen`.
- Kept `Modifier.align(...)` inside `ColumnScope`, where the public scoped extension is available.
- Expanded the Phase 3 validator to reject explicit imports of scoped Compose modifiers including `align`, `weight`, `matchParentSize`, and alignment helpers.

## Phase 3 - Complete Study Plan management

- Added guided create and edit flows with plan-specific preferences.
- Added whole-graph transactional duplication across all plan-owned Room tables.
- Added default-plan selection, dashboard switching, pause/resume, archive/restore, trash, recovery, and permanent deletion.
- Added current, archived, and trash plan collections with destructive-action confirmations.
- Added per-plan settings for adaptive planning, reviews, strict exam mode, preferred sessions, and quiet hours.
- Sealed backup XML, plural resources, locale targeting, lint advisories, tests, documentation, and phased Devtool validation.

## Phase 2 r6 - JVM testing variant boundary

- Removed the unnecessary `:core:testing` dependency on Android module `:core:data`.
- Kept shared fixtures in the plain JVM graph through `:core:domain` and `:core:model`.
- Added a repository-wide validator that rejects JVM-to-Android project dependencies.
- Added ADR-0006 documenting deterministic test-runtime variant resolution.

## Phase 2 r5 - Domain and Room database

- Restored AGP 9 built-in Kotlin and the new Android DSL.
- Kept KSP, Compose, and Serialization on the unified convention-plugin classpath.
- Removed the incompatible `org.jetbrains.kotlin.android` application from Android conventions.
- Added contract checks that reject hybrid built-in-Kotlin/legacy-DSL configurations.

## Phase 2 - Domain and Room database

- Added the complete StudyFlow domain graph.
- Added the `:core:database` Room module and schema version 1.
- Replaced volatile application storage with `RoomStudyPlanRepository`.
- Added cascade ownership, one-time sample seeding, tests, and Devtool validation.

## 0.1.0-phase01 - 2026-07-30

- Implement Phase 0 product and behavior contracts.
- Implement Phase 1 Android foundation with Kotlin, Compose and modular build logic.
- Add English and Brazilian Portuguese UI resources.
- Add responsive phone and expanded-width navigation.
- Add plan list, creation, dashboard and settings flows.
- Add DataStore preferences and replaceable Study Plan repository contracts.
- Add `.devtool.toml` with the `studyflow_android` target.

## Phase 4 r3 BuildConfig generation seal

- Enables `android.buildFeatures.buildConfig = true` in `:app`, the only module that reads `BuildConfig.VERSION_NAME`.
- Adds a Phase 4 contract that rejects `BuildConfig` references when generation is disabled or the consumer package does not match the application namespace.
- Keeps the data-layer boundary correction from r2 unchanged.

## Phase 4 + 5 Bridge

- Combines Phase 4 portability/snapshots and Phase 5 syllabus/content management for repositories that are still on the Phase 3 baseline.
- Updates `.devtool.toml` with the Phase 5 focused validation tasks expected by the acceptance contract.

## Phase 6: Schedule and Study Strategy

- Added schedule and study-strategy management through `:feature:schedule`.
- Added Room-backed schedule repository and deterministic weekly activity generation.
- Added missed-activity recovery while preserving locked activities.
- Added dashboard navigation into schedule management.
- Removed unused Phase 5 subject strings reported by lint.

## Phase 4+5 Bridge r2

- Rebuilt the bridge against the uploaded Phase 3 baseline.
- Added the missing `feature/subjects/consumer-rules.pro` required by the Android library convention plugin.
- Kept `PlanPortabilityScreen` resource reads on `stringResource`/`pluralStringResource` and added a narrow lint suppression for the false-positive `LocalContextGetResourceValueCall` triggered by Storage Access Framework `ContentResolver` usage.
- Removed `.devtool.toml` from the overlay payload; use repeated `--task` arguments for focused validation instead.
## Phase 7 - Study Sessions and Timer Execution

- Added active study-session timer execution.
- Added quick sessions and planned-activity session starts.
- Added pause, resume, finish, cancel, notes, ratings, and history.
- Added Room-backed study-session repository and domain timer rules.


## Phase 8: Question bank and practice execution

- Added `:feature:practice` for question-bank management and practice-test launching.
- Added `ManageStudyPractice`, `PracticeAnalytics` and `StudyPracticeRepository`.
- Added Room-backed practice repository and entity mappers for questions, tests and mock exams.
- Added dashboard navigation to Questions and mocks.
- Kept Room schema version 2; no migration required.

## Phase 9: Reviews and Spaced Repetition

- Added the `:feature:reviews` module with a bilingual review queue and flashcard creation screen.
- Added `StudyReviewRepository`, `ManageStudyReview`, `ReviewQueue`, `ReviewSummary`, and
  `SpacedRepetitionScheduler`.
- Added Room-backed review persistence through `RoomStudyReviewRepository` and retention mappers.
- Extended `StudyRetentionDao` with find, upsert, and delete operations for review items and
  flashcards plus error-entry lookup.
- Added dashboard navigation for reviews and flashcards.
- Added a deterministic spaced-repetition algorithm with ease-factor, interval, repetition, and
  failed-grade reset behavior.
- Reused existing schema version 2 review tables; no Room migration was needed.
- Added Phase 9 validators and included `validation.script.sha256` in the artifact manifest.
## Phase 10 - Library and Study Resources

- Added the study library execution layer for materials, video resources, and annex metadata.
- Added a dashboard route and feature module for organizing resource lists without exposing Room to the app.
- Added Phase 10 validation contracts and kept prior warning seals active.
## Phase 11 - Goals, Notes, and Progress Command Center

- Added a progress command center for plan-scoped goals and notes.
- Added Room-backed support for creating, updating, pinning, archiving, and deleting goals and notes.
- Added progress summary calculations that are independent of UI text and safe for tests.

## Phase 12 - Analytics and Insights

- Added the unified analytics feature for study consistency, weak-area detection, review backlog,
  practice trends, and schedule adherence.
- Added a Room-backed analytics repository behind the data-layer boundary.
- Added a dashboard route and Phase 12 contracts without changing the Room schema.

## Phase 13 - Settings, Backup Polish, and App Shell Seal

- Added app-shell readiness checks for language, theme, default plan, backup access, and accessibility polish.
- Added a backup entry from Settings for the current default Study Plan.
- Added Phase 13 validation contracts and warning-seal documentation.

## Phase 14 - Polish, Accessibility, and UX Consistency

- Added the final UX polish auditor for accessibility, localization, feedback, empty states,
  and navigation consistency.
- Added settings readiness cards for MVP polish status without changing persistence.
- Added Phase 14 contracts and documentation for the screen-by-screen polish pass.

## Phase 15 - Final Integration Seal and Regression Suite

- Added the MVP release-readiness seal and regression index.
- Added final cross-feature repository contracts for feature modules, validators, resource parity,
  database-boundary hygiene, and app-shell readiness.
- Added Phase 15 release report and final acceptance documentation.

## Phase 16 - Roadmap and Validation Truth Cleanup

- Reconciled the final 15-phase MVP documentation with the post-MVP gap-closure roadmap.
- Replaced stale validation-report language that referenced old Phase 5 lint warnings.
- Added contracts that prevent the roadmap and validation report from drifting away from the implemented phase set.

## Phase 17 - Annex File Storage and Portability

- Added app-private annex byte storage with SHA-256 verification and orphan cleanup.
- Added annex payload support to `.studyplan` packages while preserving metadata-only exports.
- Added library UI actions for attaching and deleting stored annex files.
- Added Phase 17 contracts, package safety checks, and validation coverage.

## Phase 18 - Material Viewer and Media Playback

- Added material, video, and annex launch planning for safe viewer/playback handoff.
- Added app package visibility queries and FileProvider routing for app-private annex copies.
- Added library screen open actions and playback-open progress hooks.

## Phase 19 - Practice/Test/Mock Execution Engine

- Added persisted answer-ledger execution for practice tests and mock exams.
- Added practice attempt start/resume, answer, finish, and summary support.
- Added interactive runner controls to the practice screen for tests and mocks.

## Phase 21 - Reminders, Notifications, and Study Widgets

- Added the reminders center for review reminders, quiet hours, notification permission readiness, and Today widget preview.
- Added Android POST_NOTIFICATIONS declaration with API-aware runtime request flow.
- Added a home-screen Today widget provider and metadata.
- Added deterministic reminder planning for due reviews, planned study blocks, missed activity recovery, and daily summaries.
- Added Phase 21 validation coverage and fixed a pre-existing duplicate Settings navigation icon parameter.

---

## Phase 20: Wrong-Answer Notebook and Remediation Loop

- Added automatic wrong-answer capture from completed practice tests and mock exams.
- Added recurrence-aware error entries, error-review scheduling, and resolve/delete flows.
- Added wrong-answer notebook UI inside Reviews and flashcards.
- Added `WrongAnswerRemediator` for priority and remediation suggestions.
- Added DAO/repository support for finding recurring question errors and deleting resolved errors.
- Added Phase 20 verifier.

# Changelog

## Debug Workbench D6 - Study engine lab

- Added aggregate-only Study Engine Lab diagnostics for schedule expansion, review queues, practice decks, mock exams, recommendations, search, progress, analytics, and sync foundation.
- Added missing-plan, cancellation, fatal-error, duplicate-kind, and bounded aggregate model tests.
- Kept the lab read-only and rejected labels, names, notes, questions, answers, and raw search/recommendation titles from diagnostics.

## Phase 22+23 r3 annex payload privacy repair

- Fixed metadata-private annex payload manifests to declare the exported pseudonymous annex ID instead of the source annex ID.
- Added focused privacy tests and verifier coverage for private annex manifest IDs, ZIP entry paths, and raw-ID rejection.

## Phase 5 - Syllabus, subjects and nested topics

- Added the `:feature:subjects` module with a bilingual Study Content screen.
- Added subject and nested-topic CRUD, search, active/mastered/attention/archive filters,
  position/name/priority/progress sorting, selection, bulk edit, archive/restore, delete, and
  topic reordering.
- Added structured-text and CSV syllabus import with preview and per-plan transactional import.
- Added `StudyContentRepository`, `ManageStudyContent`, `StudyProgressCalculator`, and
  `SyllabusImportParser` in the domain layer.
- Added Room-backed `RoomStudyContentRepository` through the public data-layer factory while
  keeping `:core:database` hidden from `:app`.
- Migrated Room schema from version 1 to 2 with nullable `examStageId` columns on subjects and
  topics plus migration coverage.
- Extended Phase 4 portability normalization so older `.studyplan` packages without exam-stage
  links remain importable.
- Added derived topic, subject, and plan progress based on topic tree completion and weights.
- Added a dashboard entry point for syllabus management.
- Fixed the Phase 4 portability lint issue by replacing `LocalContext.current` resource lookups with
  `stringResource` and `pluralStringResource` while keeping `LocalContext` only for platform access.
- Added Phase 5 validation seals for syllabus structure, migration wiring, Compose resource lookup
  safety, scoped modifier imports, and data-layer boundaries.

## Phase 4 - Portable Study Plans and warning seal

- Added the pure Kotlin `:core:portability` module and versioned `.studyplan` ZIP format.
- Added SHA-256 manifest and payload verification, bounded archive reading, path traversal defense,
  duplicate-entry rejection, exact table-inventory validation, and schema consistency checks.
- Added full, privacy-stripped, structure-only, and reusable-template exports.
- Added import preview plus create-as-new, replace-existing, and compatible-merge strategies.
- Added transactional Room graph import with complete primary- and foreign-key remapping.
- Preserved all existing snapshot metadata when replacing a plan and added automatic pre-import and
  pre-restore safety snapshots.
- Added manual snapshot creation, restore, deletion, and app-private package storage.
- Added Storage Access Framework import/export UI and plan-list/dashboard integration.
- Sanitized annex references and explicitly deferred physical annex payloads to Phase 9.
- Cleared all 15 Phase 3 warnings: AutoMirrored icons, modifier parameter ordering,
  `mutableIntStateOf`, and Portuguese `many` plural quantities.

## Phase 3 r3 - Compose scoped-modifier import hotfix

- Removed the explicit `androidx.compose.foundation.layout.align` import from `PlanListScreen`.
- Kept `Modifier.align(...)` inside `ColumnScope`, where the public scoped extension is available.
- Expanded the Phase 3 validator to reject explicit imports of scoped Compose modifiers including `align`, `weight`, `matchParentSize`, and alignment helpers.

## Phase 3 - Complete Study Plan management

- Added guided create and edit flows with plan-specific preferences.
- Added whole-graph transactional duplication across all plan-owned Room tables.
- Added default-plan selection, dashboard switching, pause/resume, archive/restore, trash, recovery, and permanent deletion.
- Added current, archived, and trash plan collections with destructive-action confirmations.
- Added per-plan settings for adaptive planning, reviews, strict exam mode, preferred sessions, and quiet hours.
- Sealed backup XML, plural resources, locale targeting, lint advisories, tests, documentation, and phased Devtool validation.

## Phase 2 r6 - JVM testing variant boundary

- Removed the unnecessary `:core:testing` dependency on Android module `:core:data`.
- Kept shared fixtures in the plain JVM graph through `:core:domain` and `:core:model`.
- Added a repository-wide validator that rejects JVM-to-Android project dependencies.
- Added ADR-0006 documenting deterministic test-runtime variant resolution.

## Phase 2 r5 - Domain and Room database

- Restored AGP 9 built-in Kotlin and the new Android DSL.
- Kept KSP, Compose, and Serialization on the unified convention-plugin classpath.
- Removed the incompatible `org.jetbrains.kotlin.android` application from Android conventions.
- Added contract checks that reject hybrid built-in-Kotlin/legacy-DSL configurations.

## Phase 2 - Domain and Room database

- Added the complete StudyFlow domain graph.
- Added the `:core:database` Room module and schema version 1.
- Replaced volatile application storage with `RoomStudyPlanRepository`.
- Added cascade ownership, one-time sample seeding, tests, and Devtool validation.

## 0.1.0-phase01 - 2026-07-30

- Implement Phase 0 product and behavior contracts.
- Implement Phase 1 Android foundation with Kotlin, Compose and modular build logic.
- Add English and Brazilian Portuguese UI resources.
- Add responsive phone and expanded-width navigation.
- Add plan list, creation, dashboard and settings flows.
- Add DataStore preferences and replaceable Study Plan repository contracts.
- Add `.devtool.toml` with the `studyflow_android` target.

## Phase 4 r3 BuildConfig generation seal

- Enables `android.buildFeatures.buildConfig = true` in `:app`, the only module that reads `BuildConfig.VERSION_NAME`.
- Adds a Phase 4 contract that rejects `BuildConfig` references when generation is disabled or the consumer package does not match the application namespace.
- Keeps the data-layer boundary correction from r2 unchanged.

## Phase 4 + 5 Bridge

- Combines Phase 4 portability/snapshots and Phase 5 syllabus/content management for repositories that are still on the Phase 3 baseline.
- Updates `.devtool.toml` with the Phase 5 focused validation tasks expected by the acceptance contract.

## Phase 6: Schedule and Study Strategy

- Added schedule and study-strategy management through `:feature:schedule`.
- Added Room-backed schedule repository and deterministic weekly activity generation.
- Added missed-activity recovery while preserving locked activities.
- Added dashboard navigation into schedule management.
- Removed unused Phase 5 subject strings reported by lint.

## Phase 4+5 Bridge r2

- Rebuilt the bridge against the uploaded Phase 3 baseline.
- Added the missing `feature/subjects/consumer-rules.pro` required by the Android library convention plugin.
- Kept `PlanPortabilityScreen` resource reads on `stringResource`/`pluralStringResource` and added a narrow lint suppression for the false-positive `LocalContextGetResourceValueCall` triggered by Storage Access Framework `ContentResolver` usage.
- Removed `.devtool.toml` from the overlay payload; use repeated `--task` arguments for focused validation instead.
## Phase 7 - Study Sessions and Timer Execution

- Added active study-session timer execution.
- Added quick sessions and planned-activity session starts.
- Added pause, resume, finish, cancel, notes, ratings, and history.
- Added Room-backed study-session repository and domain timer rules.


## Phase 8: Question bank and practice execution

- Added `:feature:practice` for question-bank management and practice-test launching.
- Added `ManageStudyPractice`, `PracticeAnalytics` and `StudyPracticeRepository`.
- Added Room-backed practice repository and entity mappers for questions, tests and mock exams.
- Added dashboard navigation to Questions and mocks.
- Kept Room schema version 2; no migration required.

## Phase 9: Reviews and Spaced Repetition

- Added the `:feature:reviews` module with a bilingual review queue and flashcard creation screen.
- Added `StudyReviewRepository`, `ManageStudyReview`, `ReviewQueue`, `ReviewSummary`, and
  `SpacedRepetitionScheduler`.
- Added Room-backed review persistence through `RoomStudyReviewRepository` and retention mappers.
- Extended `StudyRetentionDao` with find, upsert, and delete operations for review items and
  flashcards plus error-entry lookup.
- Added dashboard navigation for reviews and flashcards.
- Added a deterministic spaced-repetition algorithm with ease-factor, interval, repetition, and
  failed-grade reset behavior.
- Reused existing schema version 2 review tables; no Room migration was needed.
- Added Phase 9 validators and included `validation.script.sha256` in the artifact manifest.
## Phase 10 - Library and Study Resources

- Added the study library execution layer for materials, video resources, and annex metadata.
- Added a dashboard route and feature module for organizing resource lists without exposing Room to the app.
- Added Phase 10 validation contracts and kept prior warning seals active.
## Phase 11 - Goals, Notes, and Progress Command Center

- Added a progress command center for plan-scoped goals and notes.
- Added Room-backed support for creating, updating, pinning, archiving, and deleting goals and notes.
- Added progress summary calculations that are independent of UI text and safe for tests.

## Phase 12 - Analytics and Insights

- Added the unified analytics feature for study consistency, weak-area detection, review backlog,
  practice trends, and schedule adherence.
- Added a Room-backed analytics repository behind the data-layer boundary.
- Added a dashboard route and Phase 12 contracts without changing the Room schema.

## Phase 13 - Settings, Backup Polish, and App Shell Seal

- Added app-shell readiness checks for language, theme, default plan, backup access, and accessibility polish.
- Added a backup entry from Settings for the current default Study Plan.
- Added Phase 13 validation contracts and warning-seal documentation.

## Phase 14 - Polish, Accessibility, and UX Consistency

- Added the final UX polish auditor for accessibility, localization, feedback, empty states,
  and navigation consistency.
- Added settings readiness cards for MVP polish status without changing persistence.
- Added Phase 14 contracts and documentation for the screen-by-screen polish pass.

## Phase 15 - Final Integration Seal and Regression Suite

- Added the MVP release-readiness seal and regression index.
- Added final cross-feature repository contracts for feature modules, validators, resource parity,
  database-boundary hygiene, and app-shell readiness.
- Added Phase 15 release report and final acceptance documentation.

## Phase 16 - Roadmap and Validation Truth Cleanup

- Reconciled the final 15-phase MVP documentation with the post-MVP gap-closure roadmap.
- Replaced stale validation-report language that referenced old Phase 5 lint warnings.
- Added contracts that prevent the roadmap and validation report from drifting away from the implemented phase set.

## Phase 17 - Annex File Storage and Portability

- Added app-private annex byte storage with SHA-256 verification and orphan cleanup.
- Added annex payload support to `.studyplan` packages while preserving metadata-only exports.
- Added library UI actions for attaching and deleting stored annex files.
- Added Phase 17 contracts, package safety checks, and validation coverage.

## Phase 18 - Material Viewer and Media Playback

- Added material, video, and annex launch planning for safe viewer/playback handoff.
- Added app package visibility queries and FileProvider routing for app-private annex copies.
- Added library screen open actions and playback-open progress hooks.

## Phase 19 - Practice/Test/Mock Execution Engine

- Added persisted answer-ledger execution for practice tests and mock exams.
- Added practice attempt start/resume, answer, finish, and summary support.
- Added interactive runner controls to the practice screen for tests and mocks.

## Phase 22 - Global Search and Smart Recommendations

- Added Room-backed global search with content filters, recent searches, and active-content defaults.
- Added destination routing for every search result type.
- Added deterministic next-study, weak-topic, practice-deck, and overdue-resource recommendations.
- Added explanation strings backed by mastery, reviews, adherence, errors, accuracy, deadlines, and materials.

## Phase 23 - Privacy, Encryption, and Backup Hardening

- Added versioned AES-256-GCM password encryption for portable Study Plan packages.
- Added metadata privacy and selectable note/history redaction.
- Added backup integrity reporting and zero-write restore dry-runs.
- Added recovery explanations, local retention policy UI, and explicit backup-rule exclusions.

## Phase 24 - Optional cloud-sync foundation

- Added provider-neutral local sync state, change-token inspection, Settings sync entry, and local-only scope gates.

## 0.25.0-phase25

- Close the Phase 21 reminder delivery gap with inexact alarm scheduling, notification channel creation,
  boot recovery, and fresh-state suppression before posting.
- Replace the static Today widget placeholder with repository-backed live summaries.
- Extend the release readiness seal through Phase 25 and preserve Phase 24's local-only sync provider gate.

## Phase 25 r2 correction

- Fixed daily summary reminder quiet-hour suppression.
- Added a planner-level guard to prevent immediate due-review reminders from rearming into a one-minute loop after delivery.
- Added tests and verifier checks for quiet-hour and anti-spam reminder behavior.
## Phase 26 - Release validation seal

- Added the Phase 26 release validation seal model and source-contract auditor.
- Added validation blind-spot documentation for Gradle parity, Room schema validation, lint/resource checks, and packaging evidence.
- Extended repository contract verification through Phase 26 without introducing a Room migration or network capability.
## Phase 27 - Runtime smoke workbench

- Added runtime smoke probe models for reminders, widgets, backup dry-runs, search, recommendations, and sync readiness.
- Added a Debug/QA settings workbench with safe manual actions that reuse existing coordinators and avoid destructive writes.
- Added release validation linkage so the device-smoke gate can be satisfied by structured runtime probe evidence.


## Phase 27 r2

- Fixed runtime smoke workbench app integration so Settings receives sync state and runtime smoke imports resolve.
- Made the release validation seal phase-forward after Phase 27.
- Replaced placeholder backup smoke with a real inspect/dry-run/zero-write probe.
- Added stronger Phase 27 validator checks for compile-facing integration seams.


## Phase 29 r2

- Corrected the double-audit misses: no generated Python cache payloads, no literal NUL bytes, and behavior checks strengthened beyond marker validation.


## Phase 29 reminder delivery grace hotfix

- Fixed planned-activity reminders so inexact alarms that fire inside the delivery window still post after the fresh-state recheck.
- Suppressed stale planned-activity alarms outside the delivery window.
- Added reminder planner tests for late delivery, quiet hours, and completed-activity suppression.


## Phase 29B reminder/time/backup hardening

- Switched reminder day boundaries and Today widget planned counts from UTC epoch buckets to local calendar days.
- Added bounded late planned-activity reminder grace for Doze/App Standby delivery delays while preserving stale reminder suppression.
- Added notification-tap routing, permission-grant reminder reconciliation, time/timezone/package replacement recovery, and encrypted cloud-backup capability hardening.

## Debug Workbench D7 Runtime Self-Check and Support Export Seal

- Added bounded runtime self-check history for read-only Debug Workbench probe suites.
- Added a support-bundle ZIP seal that rejects traversal entries, database payloads, missing manifests, missing checksums, and missing redaction reports before sharing.
- Surfaced self-check history and support-bundle seal state in Debug Workbench reports.
- Added D7 validators and behavior tests for safe-suite boundaries, cancellation, fatal errors, and corrupt support bundles.
## Debug Workbench D8 Final Release Seal

- Added the final Debug Workbench release-seal policy and tests.
- Fixed D7 self-check and support-bundle seal state wiring in the workbench screen.
- Expanded repository validation to cover Phase 0 through Phase 29 and Debug Workbench D1 through D8.
- Added the D8 validator and release-seal documentation.
## Debug Workbench D8 r2 hotfix

- Fixed two JUnit `@Test(expected = ...)` tests so they expose explicit `Unit` block bodies and do not fail as `InvalidTestClassError`.
- Hardened `verify_debug_workbench_d1_r2.py` to skip generated Gradle `build/` directories while preserving cache/bytecode source guards.
- Extended the D8 verifier so those exact fixes are enforced by the final seal.


## Debug Workbench D8 r3 final release seal hotfix

- Supersedes the D8 r2 hotfix by forwarding D7 self-check history and support-bundle seal state into the Overview screen before the screen renders those cards.
- Hardened the D8 verifier so the same screen-boundary compile regression is rejected before Gradle reaches `:feature:settings:compileDebugKotlin`.


### D8 r5 lint-floor hotfix

- Routes notification channel importance reads through an API-guarded helper so minSdk 23 lint does not flag `NotificationChannel.getImportance`.
- Replaces `java.util.Base64` mapper use with `android.util.Base64` and `Base64.NO_WRAP` to preserve encoding while supporting the app minSdk.
- Extends the D8 verifier to reject both regressions.

D8 r6 lint-floor hotfix keeps every prior D8 fix and replaces Android UI java.time calls with minSdk-23-safe java.text/java.util date helpers in progress, schedule, and sessions screens.
