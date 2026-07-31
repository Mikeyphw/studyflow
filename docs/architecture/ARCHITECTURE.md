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
├── core:model
└── core:portability

core:database
├── core:model
└── core:portability

core:domain
├── core:model
└── core:portability

core:portability
└── Kotlin/JVM + kotlinx.serialization

core:testing
├── core:domain
└── core:model
```

Features do not depend on other feature implementations. The application module assembles them.

## Persistence

`StudyFlowDataLayer` constructs the production `RoomStudyPlanRepository` internally and exposes it through the domain contract. `InMemoryStudyPlanRepository` remains a
test and preview tool. The Room schema treats `StudyPlan` as the ownership root and cascades plan
deletion to every plan-owned table. Optional links to supporting children use `SET_NULL` when
historical records should remain valid.

File bytes never belong in Room. Snapshot package files live in app-private storage, while Room
stores their metadata and stable relative references. Annex metadata is portable in Phase 4, but
physical annex bytes remain deferred to Phase 9 and will use private or user-selected storage.

## UI state

Each stateful feature exposes an immutable UI-state model from a ViewModel. Stateless screen
composables receive values and event callbacks, which supports previews and isolated UI tests.

## Build logic

The included `build-logic` build owns common Android and Kotlin configuration. Compose,
Serialization, and KSP are applied through convention plugins so their Gradle classes share one
controlled classloader. Android modules retain AGP 9 built-in Kotlin and the new DSL. Plain JVM
modules, including `:core:testing`, depend only on Android-free domain and model modules so Gradle
never has to guess between Android debug and release variants.

## Study Plan lifecycle

`ManageStudyPlan` owns lifecycle transitions while `RoomStudyPlanRepository` performs every mutation
transactionally. `StudyPlanGraphDuplicator` copies the full structured child graph using a new plan
namespace for primary and foreign identifiers. Default-plan selection remains an app-level DataStore
preference, while behavioral settings remain plan-owned Room data.


## Study Plan portability

`:core:portability` defines the versioned package contract and performs bounded ZIP/JSON/checksum
processing without Android dependencies. `StudyPlanGraphPortabilityStore` maps the supported logical
Room graph to typed portable rows and imports it transactionally. `RoomStudyPlanPortabilityRepository`
owns Storage Access Framework streams and private snapshot files. Replace imports update the root and
clear children explicitly rather than deleting the root, preserving all existing snapshot metadata.
