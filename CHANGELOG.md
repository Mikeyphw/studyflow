# Changelog

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
