# StudyFlow Android

StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study.

This repository implements **Roadmap Phases 0 through 29**:

- an executable product and behavior contract;
- an English-only codebase with English and Brazilian Portuguese UI resources;
- a modular Kotlin and Jetpack Compose foundation;
- responsive compact, medium, and expanded navigation;
- complete Study Plan creation, editing, lifecycle, dashboard, and settings screens;
- versioned `.studyplan` import, export, templates, and private snapshots;
- subject and nested-topic management with syllabus import and derived progress;
- system, light, and dark themes with optional dynamic color;
- per-app language selection;
- DataStore-backed app preferences;
- Room-backed Study Plan persistence with complete graph duplication and lifecycle management;
- reusable loading, error, and empty-state components;
- unit tests, Android lint, formatting contracts, and repository validators;
- backup/restore drill seals covering encrypted packages, wrong passwords, tamper detection,
  metadata-private annex payloads, restore dry-runs, retention truthfulness, and recovery reports;
- release-candidate polish with first-run notes, upgrade notes, resource parity, and final roadmap index;
- a Devtool-ready `studyflow_android` target for future overlays.

## Product invariant

A `StudyPlan` is the complete and independent workspace for one study objective. Future phases attach
subjects, schedules, sessions, questions, mock exams, reviews, notes, videos, annexes, and statistics
to that root object through `studyPlanId`. Phase 5 makes subjects and nested topics active
Study Plan content, including per-plan ownership, import, search, filters, and progress derivation.

## Toolchain

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Kotlin 2.4.10
- JDK 17 or newer
- Android SDK 37
- Android SDK Build Tools 36.0.0
- Compose BOM 2026.06.00

AGP 9 built-in Kotlin and the new Android DSL are used for Android modules. Do not apply
`org.jetbrains.kotlin.android`, and do not set `android.builtInKotlin=false`. KSP, Compose, and
Serialization are owned by the included convention build to avoid split plugin classloaders.

The repository includes a pinned `gradle/wrapper/gradle-wrapper.properties`. Devtool can provision
and select the matching managed Gradle distribution when a checked-in `gradlew` launcher is absent.

## Build with Devtool

```bash
devtool -r /path/to/studyflow --target studyflow_android build
```

Apply and validate future overlays with:

```bash
devtool --copy --auto-hud --hud-mode desktop-window --yes \
  -r /path/to/studyflow \
  apply-overlay /path/to/overlay.zip \
  --target studyflow_android \
  --validate
```

For a native Gradle installation matching 9.5.x:

```bash
gradle :app:assembleDebug
```

## Validate

```bash
./scripts/validate_repo.sh
```

Without an Android SDK, the product-contract validators still run. With an SDK and compatible Gradle
available, run:

```bash
gradle verifyFormatting checkAll :app:assembleDebug
```

Useful individual commands:

```bash
python3 scripts/verify_phase_0.py
python3 scripts/verify_phase_1.py
python3 scripts/verify_phase_2.py
python3 scripts/verify_phase_3.py
python3 scripts/verify_phase_4.py
python3 scripts/verify_phase_5.py
gradle verifyFormatting
gradle checkAll
gradle :app:assembleDebug
```

## Repository map

```text
app/                    Application shell, navigation, and app-level state
build-logic/            Convention Gradle plugins
core:data/              Room-backed repository and data orchestration
core:database/          Room entities, DAOs, schema, and database runtime
core:designsystem/      Theme and reusable Compose components
core:domain/            Repository contracts and use cases
core:i18n/              DataStore preferences and app locale integration
core:model/             Stable domain and preference models
core:navigation/        Type-safe navigation routes
core:portability/       Pure Kotlin package schema, codec, checksums, and preview models
core:testing/           Reusable fixtures and test fakes
feature:dashboard/      Initial Study Plan dashboard
feature:plans/          Plan lifecycle, editor, settings, portability, and snapshots
feature:settings/       Theme and language settings
feature:subjects/       Subject, nested topic, syllabus import, filters, and bulk-edit UI
docs/                   Product, architecture, quality, and roadmap contracts
scripts/                Repository validation entry points
```

## Current persistence and portability boundary

Structured Study Plan data is persisted with Room through `:core:database` and
`RoomStudyPlanRepository`. Preferences remain in DataStore. Phase 4 exports the logical plan graph,
not the physical Room file, through the pure Kotlin `:core:portability` codec. Private snapshots use
the same checksum-verified `.studyplan` format and are stored below app-private storage.

Annex metadata can be carried safely, but physical annex bytes remain excluded until Phase 9. Media
playback remains assigned to Phase 10. Plan duplication and package import both remap all identifiers
so no child record is shared across plan boundaries.

## Languages

- Code, identifiers, serialized fields, build logic, and technical documentation: English
- UI: English and Portuguese (Brazil)
- User-created content: stored exactly as entered

## Package

`com.mikeyphw.studyflow`

## Phase 2 persistence
Shared test fixtures remain Android-free to keep JVM test variant resolution deterministic.

Structured Study Plan data is persisted in Room through `:core:database`. The app
uses `RoomStudyPlanRepository`, while `InMemoryStudyPlanRepository` remains available
for focused tests and previews. Run `python3 scripts/verify_phase_2.py` for the static
Phase 2 acceptance gate.

## Phase 3 plan management

Phase 3 r3 also seals AGP 9 built-in Kotlin compatibility for Compose scoped modifiers by relying on `RowScope`, `ColumnScope`, and `BoxScope` member extensions instead of importing those symbols directly.

Study Plans now support guided create/edit, complete structured duplication, default-plan selection,
pause/resume, archive/restore, trash/recovery, permanent deletion, dashboard switching, and isolated
per-plan settings. Run `python3 scripts/verify_phase_3.py` for the static acceptance gate.

## Phase 4 portability

Study Plans can now be exported as complete, privacy-stripped, structure-only, or reusable-template
packages. Imports are previewed before mutation and support create-as-new, replace, and compatible
merge strategies. Replace operations preserve existing snapshot history, while replace and merge
create a private pre-import recovery point. Run `python3 scripts/verify_phase_4.py` for the Phase 4
contract.

### BuildConfig contract

The app explicitly enables `BuildConfig` generation because portable package manifests use the variant `VERSION_NAME`. The repository validator prevents source references from drifting away from the corresponding AGP build feature.

## Phase 5 syllabus and content organization

Study Plans now include active subject and nested-topic organization. The `:feature:subjects`
module exposes search, filters, sorting, topic tree editing, bulk updates, archive/restore, and
structured-text or CSV syllabus import. Progress is derived from the topic tree and written back to
the owning Study Plan. Room schema version 2 adds nullable exam-stage links for subjects and topics
through an explicit migration. Run `python3 scripts/verify_phase_5.py` for the Phase 5 contract.



## Phase 6

Adds schedule and study-strategy management, generated weekly activities, missed-work recovery and a lint warning seal for unused subject strings.
### Phase 7: Study sessions

StudyFlow now supports active study sessions, quick timers, planned activity execution, completion notes, ratings, and history.


### Phase 8: Questions and mocks

The dashboard now links to a practice cockpit. Users can add multiple-choice questions, mark favorites
or disputed items, delete questions, view practice summary metrics, and create aggregate practice tests
from the filtered question bank. The implementation uses the existing practice tables and keeps Room
behind the `:core:data` boundary.

## Phase 9 reviews and spaced repetition

StudyFlow now includes a review cockpit for due, upcoming, and recently completed review items.
Students can create flashcards, automatically seed their first review, grade due items, skip items,
and let the spaced-repetition scheduler compute the next interval. Review execution reuses the
existing `review_items`, `flashcards`, and `error_entries` tables and keeps Room behind the
`:core:data` boundary. Run `python3 scripts/verify_phase_9.py` for the Phase 9 contract.
### Phase 10: Library and Study Resources

StudyFlow now includes a resource library for study materials, video links, and annex metadata.
The library is plan-scoped, Room-backed, and connected from the dashboard.
### Phase 11: Goals, Notes, and Progress Command Center

StudyFlow now includes plan-scoped goals and notes with a dashboard route. Goals can track time,
question, accuracy, topic-completion, and custom progress while notes can be pinned and searched in later phases.

### Phase 12: Analytics and Insights

StudyFlow now has an analytics view that combines sessions, schedule execution, practice results,
reviews, goals, content progress, and library progress into actionable insights.

### Phase 13: Settings, Backup Polish, and App Shell Seal

Settings now include an app-shell readiness section and a backup entry for the default Study Plan.
This phase closes navigation, accessibility, settings, and snapshot-discovery seams before later release polish.

### Phase 14: Polish, Accessibility, and UX Consistency

StudyFlow now tracks the final MVP polish surface: accessibility labels, bilingual copy parity,
empty/error/loading state behavior, action feedback, and navigation consistency.

### Phase 15: Final Integration Seal and Regression Suite

The StudyFlow MVP baseline is sealed with an integration report covering all 15 phases, feature
module wiring, bilingual resource parity, source-quality guards, and release-readiness contracts.

### Phase 16: Roadmap and Validation Truth Cleanup

The repository now distinguishes the delivered 15-phase MVP baseline from the post-MVP gap-closure
roadmap. Validation reports and roadmap docs are kept aligned with the source tree instead of old
planning drafts.

### Phase 17: Annex File Storage and Portability

StudyFlow annexes now support real app-private file copies. Full exports can include annex payloads
inside `.studyplan` packages with checksum verification, while metadata-only exports remain available
for smaller or privacy-conscious packages.

### Phase 18: Material Viewer and Media Playback

Library resources can now be opened from StudyFlow through safe Android view intents. Imported annex
copies are shared through the app FileProvider, web/video sources use their matching viewer handoff,
and open actions update resource activity metadata.

### Phase 19: Practice/Test/Mock Execution Engine

StudyFlow now supports executable practice and mock attempts. Practice decks persist an answer
ledger inside the existing test/mock configuration payload, allowing answer, resume, finish, and
summary flows without introducing a schema migration.

## Phase 20: Wrong-Answer Notebook

StudyFlow now captures wrong answers from finished practice tests and mock exams, groups repeated mistakes
by question, schedules error-based reviews, and exposes a dedicated wrong-answer notebook inside Reviews.

### Phase 21: Reminders, Notifications, and Study Widgets

StudyFlow now includes a per-plan reminders center, Android notification-permission handling, quiet-hour suppression, notification candidate previews, and a home-screen Today widget entry point. The feature is deliberately local/offline-first and derives reminders from existing schedule and review state.

---

# StudyFlow Android

StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study.

This repository implements **Roadmap Phases 0 through 29**:

- an executable product and behavior contract;
- an English-only codebase with English and Brazilian Portuguese UI resources;
- a modular Kotlin and Jetpack Compose foundation;
- responsive compact, medium, and expanded navigation;
- complete Study Plan creation, editing, lifecycle, dashboard, and settings screens;
- versioned `.studyplan` import, export, templates, and private snapshots;
- subject and nested-topic management with syllabus import and derived progress;
- system, light, and dark themes with optional dynamic color;
- per-app language selection;
- DataStore-backed app preferences;
- Room-backed Study Plan persistence with complete graph duplication and lifecycle management;
- reusable loading, error, and empty-state components;
- unit tests, Android lint, formatting contracts, and repository validators;
- backup/restore drill seals covering encrypted packages, wrong passwords, tamper detection,
  metadata-private annex payloads, restore dry-runs, retention truthfulness, and recovery reports;
- release-candidate polish with first-run notes, upgrade notes, resource parity, and final roadmap index;
- a Devtool-ready `studyflow_android` target for future overlays.

## Product invariant

A `StudyPlan` is the complete and independent workspace for one study objective. Future phases attach
subjects, schedules, sessions, questions, mock exams, reviews, notes, videos, annexes, and statistics
to that root object through `studyPlanId`. Phase 5 makes subjects and nested topics active
Study Plan content, including per-plan ownership, import, search, filters, and progress derivation.

## Toolchain

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Kotlin 2.4.10
- JDK 17 or newer
- Android SDK 37
- Android SDK Build Tools 36.0.0
- Compose BOM 2026.06.00

AGP 9 built-in Kotlin and the new Android DSL are used for Android modules. Do not apply
`org.jetbrains.kotlin.android`, and do not set `android.builtInKotlin=false`. KSP, Compose, and
Serialization are owned by the included convention build to avoid split plugin classloaders.

The repository includes a pinned `gradle/wrapper/gradle-wrapper.properties`. Devtool can provision
and select the matching managed Gradle distribution when a checked-in `gradlew` launcher is absent.

## Build with Devtool

```bash
devtool -r /path/to/studyflow --target studyflow_android build
```

Apply and validate future overlays with:

```bash
devtool --copy --auto-hud --hud-mode desktop-window --yes \
  -r /path/to/studyflow \
  apply-overlay /path/to/overlay.zip \
  --target studyflow_android \
  --validate
```

For a native Gradle installation matching 9.5.x:

```bash
gradle :app:assembleDebug
```

## Validate

```bash
./scripts/validate_repo.sh
```

Without an Android SDK, the product-contract validators still run. With an SDK and compatible Gradle
available, run:

```bash
gradle verifyFormatting checkAll :app:assembleDebug
```

Useful individual commands:

```bash
python3 scripts/verify_phase_0.py
python3 scripts/verify_phase_1.py
python3 scripts/verify_phase_2.py
python3 scripts/verify_phase_3.py
python3 scripts/verify_phase_4.py
python3 scripts/verify_phase_5.py
gradle verifyFormatting
gradle checkAll
gradle :app:assembleDebug
```

## Repository map

```text
app/                    Application shell, navigation, and app-level state
build-logic/            Convention Gradle plugins
core:data/              Room-backed repository and data orchestration
core:database/          Room entities, DAOs, schema, and database runtime
core:designsystem/      Theme and reusable Compose components
core:domain/            Repository contracts and use cases
core:i18n/              DataStore preferences and app locale integration
core:model/             Stable domain and preference models
core:navigation/        Type-safe navigation routes
core:portability/       Pure Kotlin package schema, codec, checksums, and preview models
core:testing/           Reusable fixtures and test fakes
feature:dashboard/      Initial Study Plan dashboard
feature:plans/          Plan lifecycle, editor, settings, portability, and snapshots
feature:settings/       Theme and language settings
feature:subjects/       Subject, nested topic, syllabus import, filters, and bulk-edit UI
docs/                   Product, architecture, quality, and roadmap contracts
scripts/                Repository validation entry points
```

## Current persistence and portability boundary

Structured Study Plan data is persisted with Room through `:core:database` and
`RoomStudyPlanRepository`. Preferences remain in DataStore. Phase 4 exports the logical plan graph,
not the physical Room file, through the pure Kotlin `:core:portability` codec. Private snapshots use
the same checksum-verified `.studyplan` format and are stored below app-private storage.

Annex metadata can be carried safely, but physical annex bytes remain excluded until Phase 9. Media
playback remains assigned to Phase 10. Plan duplication and package import both remap all identifiers
so no child record is shared across plan boundaries.

## Languages

- Code, identifiers, serialized fields, build logic, and technical documentation: English
- UI: English and Portuguese (Brazil)
- User-created content: stored exactly as entered

## Package

`com.mikeyphw.studyflow`

## Phase 2 persistence
Shared test fixtures remain Android-free to keep JVM test variant resolution deterministic.

Structured Study Plan data is persisted in Room through `:core:database`. The app
uses `RoomStudyPlanRepository`, while `InMemoryStudyPlanRepository` remains available
for focused tests and previews. Run `python3 scripts/verify_phase_2.py` for the static
Phase 2 acceptance gate.

## Phase 3 plan management

Phase 3 r3 also seals AGP 9 built-in Kotlin compatibility for Compose scoped modifiers by relying on `RowScope`, `ColumnScope`, and `BoxScope` member extensions instead of importing those symbols directly.

Study Plans now support guided create/edit, complete structured duplication, default-plan selection,
pause/resume, archive/restore, trash/recovery, permanent deletion, dashboard switching, and isolated
per-plan settings. Run `python3 scripts/verify_phase_3.py` for the static acceptance gate.

## Phase 4 portability

Study Plans can now be exported as complete, privacy-stripped, structure-only, or reusable-template
packages. Imports are previewed before mutation and support create-as-new, replace, and compatible
merge strategies. Replace operations preserve existing snapshot history, while replace and merge
create a private pre-import recovery point. Run `python3 scripts/verify_phase_4.py` for the Phase 4
contract.

### BuildConfig contract

The app explicitly enables `BuildConfig` generation because portable package manifests use the variant `VERSION_NAME`. The repository validator prevents source references from drifting away from the corresponding AGP build feature.

## Phase 5 syllabus and content organization

Study Plans now include active subject and nested-topic organization. The `:feature:subjects`
module exposes search, filters, sorting, topic tree editing, bulk updates, archive/restore, and
structured-text or CSV syllabus import. Progress is derived from the topic tree and written back to
the owning Study Plan. Room schema version 2 adds nullable exam-stage links for subjects and topics
through an explicit migration. Run `python3 scripts/verify_phase_5.py` for the Phase 5 contract.



## Phase 6

Adds schedule and study-strategy management, generated weekly activities, missed-work recovery and a lint warning seal for unused subject strings.
### Phase 7: Study sessions

StudyFlow now supports active study sessions, quick timers, planned activity execution, completion notes, ratings, and history.


### Phase 8: Questions and mocks

The dashboard now links to a practice cockpit. Users can add multiple-choice questions, mark favorites
or disputed items, delete questions, view practice summary metrics, and create aggregate practice tests
from the filtered question bank. The implementation uses the existing practice tables and keeps Room
behind the `:core:data` boundary.

## Phase 9 reviews and spaced repetition

StudyFlow now includes a review cockpit for due, upcoming, and recently completed review items.
Students can create flashcards, automatically seed their first review, grade due items, skip items,
and let the spaced-repetition scheduler compute the next interval. Review execution reuses the
existing `review_items`, `flashcards`, and `error_entries` tables and keeps Room behind the
`:core:data` boundary. Run `python3 scripts/verify_phase_9.py` for the Phase 9 contract.
### Phase 10: Library and Study Resources

StudyFlow now includes a resource library for study materials, video links, and annex metadata.
The library is plan-scoped, Room-backed, and connected from the dashboard.
### Phase 11: Goals, Notes, and Progress Command Center

StudyFlow now includes plan-scoped goals and notes with a dashboard route. Goals can track time,
question, accuracy, topic-completion, and custom progress while notes can be pinned and searched in later phases.

### Phase 12: Analytics and Insights

StudyFlow now has an analytics view that combines sessions, schedule execution, practice results,
reviews, goals, content progress, and library progress into actionable insights.

### Phase 13: Settings, Backup Polish, and App Shell Seal

Settings now include an app-shell readiness section and a backup entry for the default Study Plan.
This phase closes navigation, accessibility, settings, and snapshot-discovery seams before later release polish.

### Phase 14: Polish, Accessibility, and UX Consistency

StudyFlow now tracks the final MVP polish surface: accessibility labels, bilingual copy parity,
empty/error/loading state behavior, action feedback, and navigation consistency.

### Phase 15: Final Integration Seal and Regression Suite

The StudyFlow MVP baseline is sealed with an integration report covering all 15 phases, feature
module wiring, bilingual resource parity, source-quality guards, and release-readiness contracts.

### Phase 16: Roadmap and Validation Truth Cleanup

The repository now distinguishes the delivered 15-phase MVP baseline from the post-MVP gap-closure
roadmap. Validation reports and roadmap docs are kept aligned with the source tree instead of old
planning drafts.

### Phase 17: Annex File Storage and Portability

StudyFlow annexes now support real app-private file copies. Full exports can include annex payloads
inside `.studyplan` packages with checksum verification, while metadata-only exports remain available
for smaller or privacy-conscious packages.

### Phase 18: Material Viewer and Media Playback

Library resources can now be opened from StudyFlow through safe Android view intents. Imported annex
copies are shared through the app FileProvider, web/video sources use their matching viewer handoff,
and open actions update resource activity metadata.

### Phase 19: Practice/Test/Mock Execution Engine

StudyFlow now supports executable practice and mock attempts. Practice decks persist an answer
ledger inside the existing test/mock configuration payload, allowing answer, resume, finish, and
summary flows without introducing a schema migration.

## Phase 20: Wrong-Answer Notebook

StudyFlow now captures wrong answers from finished practice tests and mock exams, groups repeated mistakes
by question, schedules error-based reviews, and exposes a dedicated wrong-answer notebook inside Reviews.

### Phase 22: Global Search and Smart Recommendations

StudyFlow now searches plans, subjects, topics, questions, notes, materials, flashcards, and the wrong-answer
notebook from one screen. Deterministic recommendations explain the next study action using the local study graph.

### Phase 23: Privacy, Encryption, and Backup Hardening

StudyFlow can now password-encrypt `.studyplan` packages, redact notes/history/identifying metadata, inspect backup
integrity, and simulate a restore with zero database writes. A privacy center documents recovery and retention truth.

### Phase 24 sync foundation

StudyFlow now exposes a local-only sync foundation. No cloud provider, account identity, token, or remote merge is implemented in this phase.

### Phase 25 post-MVP gap closure

Phase 25 seals the post-MVP roadmap by completing local reminder delivery and dynamic widgets while keeping
cloud sync provider work gated behind the Phase 24 local-only foundation.

## Phase 25 r2 correction

Phase 25 reminder delivery now suppresses daily summaries during quiet hours and prevents immediate due-review reminder loops after delivery.

## Phase 26: Release validation seal

- Adds a release validation model that separates marker validators from Gradle-backed evidence.
- Documents the exact remaining gates: configuration, compilation, unit tests, resource merge, lint, assemble, and device smoke.
- Extends the source-contract chain through Phase 26.

## Phase 27: Runtime smoke workbench

- Adds a settings-accessible QA workbench for reminder, widget, backup, search, recommendation, and sync smoke probes.
- Keeps probes safe: widget refresh and reminder reconciliation are allowed, backup/import remains dry-run-oriented.
- Extends the contract chain through Phase 27.
