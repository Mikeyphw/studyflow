# Architecture

## Direction

StudyFlow uses Kotlin, Jetpack Compose, unidirectional data flow, repository abstractions,
ViewModel state holders, Room, and feature-oriented modules.

## Dependency direction

```text
app
├── feature modules
├── core:data
├── core:designsystem
├── core:i18n
└── core:navigation

feature modules
├── core:domain
├── core:model
└── core:designsystem

core:data
├── core:database
├── core:domain
└── core:model

core:database
└── core:model

core:domain
└── core:model

core:testing
├── core:domain
└── core:model
```

Features do not depend on other feature implementations. The application module assembles them.

## Persistence

`RoomStudyPlanRepository` is the production repository. `InMemoryStudyPlanRepository` remains a
test and preview tool. The Room schema treats `StudyPlan` as the ownership root and cascades plan
deletion to every plan-owned table. Optional links to supporting children use `SET_NULL` when
historical records should remain valid.

File bytes never belong in Room. Future annex and snapshot phases will store file metadata and
stable references in the database while keeping payloads in private or user-selected storage.

## UI state

Each stateful feature exposes an immutable UI-state model from a ViewModel. Stateless screen
composables receive values and event callbacks, which supports previews and isolated UI tests.

## Build logic

The included `build-logic` build owns common Android and Kotlin configuration. Compose,
Serialization, and KSP are applied through convention plugins so their Gradle classes share one
controlled classloader. Android modules retain AGP 9 built-in Kotlin and the new DSL. Plain JVM
modules, including `:core:testing`, depend only on Android-free domain and model modules so Gradle
never has to guess between Android debug and release variants.
