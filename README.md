# StudyFlow Android

StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study.

This repository implements **Roadmap Phases 0 through 3**:

- an executable product and behavior contract;
- an English-only codebase with English and Brazilian Portuguese UI resources;
- a modular Kotlin and Jetpack Compose foundation;
- responsive compact, medium, and expanded navigation;
- complete Study Plan creation, editing, lifecycle, dashboard, and settings screens;
- system, light, and dark themes with optional dynamic color;
- per-app language selection;
- DataStore-backed app preferences;
- Room-backed Study Plan persistence with complete graph duplication and lifecycle management;
- reusable loading, error, and empty-state components;
- unit tests, Android lint, formatting contracts, and repository validators;
- a Devtool-ready `studyflow_android` target for future overlays.

## Product invariant

A `StudyPlan` is the complete and independent workspace for one study objective. Future phases attach
subjects, schedules, sessions, questions, mock exams, reviews, notes, videos, annexes, and statistics
to that root object through `studyPlanId`.

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
core:testing/           Reusable fixtures and test fakes
feature:dashboard/      Initial Study Plan dashboard
feature:plans/          Plan lifecycle, guided editor, collections, and plan settings
feature:settings/       Theme and language settings
docs/                   Product, architecture, quality, and roadmap contracts
scripts/                Repository validation entry points
```

## Current persistence boundary

Structured Study Plan data is persisted with Room through `:core:database` and
`RoomStudyPlanRepository`. Preferences remain in DataStore. Import/export packages, annex bytes,
and media playback remain assigned to their later roadmap phases. Plan duplication already clones the
complete structured Room graph and remaps all internal identifiers.

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
