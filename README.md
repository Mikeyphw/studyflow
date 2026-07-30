# StudyFlow Android

StudyFlow is an offline-first Android study workspace for public examinations, certifications,
university courses, entrance examinations, and independent study.

This repository implements **Roadmap Phase 0 and Phase 1**:

- an executable product and behavior contract;
- an English-only codebase with English and Brazilian Portuguese UI resources;
- a modular Kotlin and Jetpack Compose foundation;
- responsive compact, medium, and expanded navigation;
- plan list, empty state, plan creation, dashboard, and settings screens;
- system, light, and dark themes with optional dynamic color;
- per-app language selection;
- DataStore-backed app preferences;
- in-memory Study Plan repository for the foundation milestone;
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

AGP 9 built-in Kotlin support is used for Android modules. Do not apply
`org.jetbrains.kotlin.android`.

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
gradle verifyFormatting
gradle checkAll
gradle :app:assembleDebug
```

## Repository map

```text
app/                    Application shell, navigation, and app-level state
build-logic/            Convention Gradle plugins
core:data/              Foundation in-memory repository
core:designsystem/      Theme and reusable Compose components
core:domain/            Repository contracts and use cases
core:i18n/              DataStore preferences and app locale integration
core:model/             Stable domain and preference models
core:navigation/        Type-safe navigation routes
core:testing/           Reusable fixtures and test fakes
feature:dashboard/      Initial Study Plan dashboard
feature:plans/          Plan list, empty state, and creation flow
feature:settings/       Theme and language settings
docs/                   Product, architecture, quality, and roadmap contracts
scripts/                Repository validation entry points
```

## Current persistence boundary

Phase 1 intentionally uses an in-memory Study Plan repository. Preferences are persisted with
DataStore. Room persistence, migrations, import/export packages, videos, and annex storage begin in
later roadmap phases. The repository contract prevents the UI from depending on the temporary store.

## Languages

- Code, identifiers, serialized fields, build logic, and technical documentation: English
- UI: English and Portuguese (Brazil)
- User-created content: stored exactly as entered

## Package

`com.mikeyphw.studyflow`
