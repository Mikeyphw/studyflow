# ADR-0009: Keep Room behind the data-layer composition boundary

## Status

Accepted in Phase 4 r2.

## Context

The application composition root previously imported `StudyFlowDatabase` and constructed Room-backed
repositories directly. However, `:app` depended only on `:core:data`, while `:core:data` declared
`:core:database` as an `implementation` dependency. This caused the application compile classpath to
miss `StudyFlowDatabase` and also leaked a data-source implementation across module boundaries.

## Decision

`:core:data` exposes `createStudyFlowDataLayer`, which constructs one `StudyFlowDatabase` and all
Room-backed repositories internally. The returned `StudyFlowDataLayer` exposes only domain repository
interfaces and an initialization operation.

The concrete Room repositories are internal to `:core:data`. `:core:database` remains an
`implementation` dependency, and `:app` does not import or depend directly on Room types.

## Consequences

- The application module compiles using only the public API of `:core:data`.
- Room can be replaced or reorganized without changing the application composition root.
- Gradle `implementation` semantics match the Kotlin public API surface.
- One database instance is created for the application-scoped data layer.
- Repository implementations remain testable inside `:core:data` while consumers use domain contracts.
