# Architecture

## Direction

StudyFlow uses Kotlin, Jetpack Compose, unidirectional data flow, repository abstractions, ViewModel
state holders, and feature-oriented modules.

## Dependency direction

```text
app
├── feature modules
├── core:designsystem
├── core:i18n
└── core:navigation

feature modules
├── core:domain
├── core:model
└── core:designsystem

core:data
├── core:domain
└── core:model

core:domain
└── core:model
```

Features do not depend on other feature implementations. The application module assembles them.

## Foundation persistence

`InMemoryStudyPlanRepository` is deliberately temporary. Screens depend on `StudyPlanRepository`, so
Phase 2 can replace the implementation with Room without rewriting UI contracts.

## UI state

Each stateful feature exposes an immutable UI-state model from a ViewModel. Stateless screen
composables receive values and event callbacks, which supports previews and isolated UI tests.

## Build logic

The included `build-logic` build owns common Android and Kotlin configuration. Android modules use
AGP 9 built-in Kotlin and the Kotlin Compose compiler plugin.
