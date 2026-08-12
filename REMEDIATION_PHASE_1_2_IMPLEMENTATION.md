# StudyFlow remediation Phase 1 + Phase 2

This change set combines the first two dependency-ordered remediation overlays from the BH-01 through BH-64 master audit into one Devtool overlay.

## Contract closed by this overlay

Phase 1 / OV-01:

- RM-003 — deterministic build/runtime validation foundation
- RM-004 — one process-owned Room/data graph
- RM-005 — explicit application/workspace loading state and one-shot reminder launches

Phase 2 / OV-02:

- RM-010 — core model/identifier invariants
- RM-011 — Room same-plan ownership and logical integrity foundation
- RM-012 — v2→v3 active Focus target-duration backfill
- RM-013 — destructive DAO/corruption-hiding foundation fixes
- RM-014 — strict mapper/codec corruption handling and structure mapping symmetry
- RM-056 foundation — conditional/atomic DAO mutation primitives for later feature overlays

The later feature overlays are still responsible for wiring every domain workflow to the new compare-and-set/idempotency primitives. This overlay establishes those primitives and uses them immediately in the highest-risk Study Session paths; it does not claim later Practice/Review/Adaptive feature remediation is complete.

## Phase 1 — build/runtime foundation

### Complete repository launcher

The repository now contains a complete `./gradlew` launcher set:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

The bootstrap verifies the configured Gradle distribution SHA-256 before extraction.

### Fail-closed Android validation

`scripts/validate_repo.sh` now treats a missing Gradle launcher or Android SDK as a validation failure instead of printing a successful `SKIPPED` result.

The normal validation contract includes Debug and Release work:

- `verifyFormatting`
- `checkAll`
- `:app:assembleDebug`
- `:app:assembleRelease`

`checkAll` now includes Release compilation and Release lint, and `.devtool.toml` includes matching Release compile/lint/package tasks.

### One process-owned application graph

`StudyFlowApplication` owns the application `AppContainer`, data layer and Room database for the process. Widget and reminder receivers reuse that application-owned graph rather than constructing new `StudyFlowDataLayer`/Room instances.

Reminder reconciliation refreshes widgets once after an all-plan pass instead of once per plan.

### Explicit startup/loading state

The application root now distinguishes:

- initializing
- ready
- failed

Persisted preferences, plans and sync state are no longer replaced with fake business defaults while their flows are still loading.

Today, Plan, Progress and Practice workspaces now wait for all required data sources instead of interpreting empty placeholders as real user data. The shared load contract distinguishes `Loading`, `Ready`, `Missing` and `Failed`; plan destinations use `Missing` only after the nullable plan Flow has actually emitted, and failed reads can re-subscribe through a real Retry action. Legacy Edit Plan, Plan Settings, Sessions and Reminders routes use the same plan load gate instead of treating the first `null` placeholder as an unknown plan.

### One-shot notification launch intent

Reminder launch extras are consumed and removed from the Activity intent before validation. Activity recreation therefore cannot replay the same reminder-navigation request.

## Phase 2 — database integrity and atomic mutation foundation

### Room schema version 4

Schema v4 retains the v3 entity layout and adds database-enforced integrity through triggers installed on fresh database creation and by the 3→4 migration. Before those guards are installed during migration, `verifyExistingData` rejects pre-existing foreign-key, plan-scope, blank-identity and duplicate-active-session corruption rather than sealing invalid legacy state into v4.

The integrity layer enforces, where applicable:

- stable `studyPlanId` ownership after insert
- same-plan foreign relationships for StudyFlow logical references
- subject/topic/stage/target ownership consistency
- question/review/error/flashcard ownership consistency
- material/video/annex/session ownership consistency
- one RUNNING/PAUSED Study Session per plan
- nonblank identifiers and representative enum/range/boolean invariants
- immutable Study Plan Settings ownership identity
- Subject/Topic ExamStage cleanup when an ExamStage is deleted, preventing dangling logical references not represented as Room foreign keys

### Migration correctness

The migration chain is now explicitly:

- 1→2
- 2→3
- 3→4

The 2→3 migration backfills `study_sessions.targetDurationMinutes` from the linked `planned_activities.plannedDurationMinutes` when available. This preserves the countdown target for a legacy active Focus Session during upgrade.

### Safer repository boundary, schedule replacement and active-session lookup

`AppContainer` no longer exposes mutable StudyFlow repositories. Production app code consumes `ManageStudy*` operations, while concrete repositories remain private composition details. This closes the audited path that allowed UI/platform code to bypass domain validation.

Unlocked schedule regeneration now removes only replaceable `PLANNED`/`MISSED` rows, rather than deleting completed or in-progress history.

Active-session lookup returns all matching active rows and treats multiplicity as corruption instead of hiding it behind `LIMIT 1`.

### Conditional/field-specific mutation primitives

DAO primitives now include conditional transitions for high-risk mutable state, including:

- planned-activity compare-and-set
- Study Session status compare-and-set
- active Study Session note-only update
- Review state compare-and-set
- atomic ErrorEntry recurrence increment
- submit-if-open StudyTest transition

Study Session note updates now modify only the notes field of an active session, preventing a stale full-row note save from overwriting a newer pause/completion transition.

The highest-risk Study Session lifecycle now consumes the atomic foundation immediately: creation is transactional, pause/resume/cancel use conditional status updates, completion uses a conditional completion update, and linked planned-activity changes use compare-and-set status updates in the same Room transaction. A stale expected status therefore fails instead of overwriting newer state.

Study Session persistence also revalidates current linked-activity state and resource ownership in the Room transaction.

### Model invariants

Core identifiers reject blank values. Model constructors now enforce the central invariants needed by the audited persistence boundary, including strict platform-safe ISO dates, duration/count/rating ranges, and multiple-choice correctness consistency.

### Strict mapper/codec behavior

Mapper enum decoding now reports structured database-corruption failures instead of letting raw `enumValueOf` crash a Flow ambiguously. Every entity-to-model mapping is also wrapped by `decodeModel`, so constructor invariant failures from corrupt persisted rows are normalized to `StudyFlowDatabaseCorruptionException` instead of escaping as raw `IllegalArgumentException`.

Encoded string lists no longer silently drop malformed elements and preserve the distinction between an empty list and a list containing an empty string.

StudyTarget and ExamStage now have symmetric entity/model mappings and repository write support.

## Regression tests and validation added

Added/expanded tests cover:

- schema v3→v4 integrity migration
- cross-plan topic/subject rejection
- duplicate active-session rejection and rejection of pre-existing duplicate-active legacy state during v3→v4
- ExamStage deletion clearing Subject/Topic logical references
- v2→v3 linked Focus target-duration backfill
- model invariants
- mapper list/enum corruption handling
- StudyTarget/ExamStage round trips
- Study Session active-note update contract

A source contract verifier, `scripts/verify_remediation_phase_1_2.py`, is registered in `verifyRepositoryContracts` so the build/runtime and DB-foundation invariants remain explicit.

## Validation performed while creating the overlay

Passed locally in the artifact sandbox:

- repository source-contract validators relevant to Phase 1/2
- formatting/source checks
- pure Kotlin compilation of the core model invariant layer
- entity + mapper compilation with minimal Android/Room stubs
- exact `StudyFlowMigrations.Migration2To3` SQL probe against SQLite, including linked target-duration backfill
- exact `StudyFlowIntegrity.install()` SQL generated from the real Kotlin implementation against exported Room v4 schema
- exact generated v4 integrity SQL compiled from `StudyFlowIntegrity` (189 triggers) and installed into the exported Room v4 schema
- exact `verifyExistingData` query set compiled from `StudyFlowIntegrity` (84 queries), including legacy cross-plan and duplicate-active corruption detection
- SQLite probes rejecting cross-plan ownership, duplicate active sessions, invalid values and Plan Settings reparenting
- exact DAO SQL probes proving stale Study Session/activity expected states are rejected and note updates cannot touch terminal sessions
- strict mapper runtime probes for unknown enums, malformed encoded lists, empty-string list round-trip, and model-constructor corruption normalization
- overlay clean-apply/byte-equivalence and ZIP integrity checks (performed when packaging)

Full Gradle/Android execution could not be performed in this sandbox because no Android SDK, system Gradle, or cached Gradle distribution is available and network access is disabled. This is intentionally **not** treated as a successful Android validation. The new repository validator fails closed in that environment.

## Required Devtool validation after applying

Run the combined foundation overlay with at least:

```text
:core:model:test
:core:database:testDebugUnitTest
:core:data:testDebugUnitTest
:core:domain:test
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:compileReleaseKotlin
:app:lintRelease
:app:assembleRelease
```

On an emulator/device also run the database migration/integrity instrumentation suite, including `:core:database:connectedDebugAndroidTest`.

## Dependency boundary

This overlay intentionally stops after Phase 2. Phase 3 (Plan identity/navigation/lifecycle) and later feature overlays must build on these foundations rather than duplicating them.
