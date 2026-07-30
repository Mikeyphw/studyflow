# Phase 0 and Phase 1 implementation

This repository implements the StudyFlow product contract and Android foundation.

## Phase 0

- Study Plan aggregate definition and lifecycle contract.
- Navigation, localization, accessibility, offline-first, portability and scope contracts.
- Architecture decisions defining English-only code and bilingual UI.
- Acceptance validators under `scripts/`.

## Phase 1

- Modular Kotlin and Jetpack Compose Android project.
- English and Brazilian Portuguese resources with a per-app language selector.
- Responsive bottom navigation and navigation rail.
- Plan list, empty state, plan creation, dashboard and settings.
- System, light and dark theme modes plus dynamic color.
- DataStore-backed preferences.
- Replaceable Study Plan repository boundary with a temporary in-memory implementation.
- Unit, lint, formatting and contract validation entry points.
- Devtool target `studyflow_android` for future overlay application and validation.
