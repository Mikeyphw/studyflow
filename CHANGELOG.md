# Changelog

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
