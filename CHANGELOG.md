# Changelog

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
