# ADR-0004: Complete Domain Model and Room Database

## Status

Accepted for Phase 2.

## Decision

StudyFlow stores structured plan data in a Room database named `studyflow.db`.
The database schema version starts at `1` and schema export is mandatory.

The `StudyPlan` aggregate remains the ownership root. Every plan-owned table has a
`studyPlanId` foreign key that deletes with `ForeignKey.CASCADE`. References to optional
children such as subjects, topics, materials, and videos use `ForeignKey.SET_NULL` so
history can survive the removal of an individual supporting item.

Domain models remain Android-free in `:core:model`. Room entities and DAOs live in
`:core:database`. Repository implementations live in `:core:data` and map database
entities to domain models before exposing them to features.

## Consequences

- Plans survive process death and application restarts.
- Deleting a plan atomically deletes all rows owned by that plan.
- Database enums are stored as stable English enum names.
- User-visible localized strings are never persisted as state.
- File bytes remain outside Room; annex rows store metadata and storage references.
- Every future database release must commit exported schemas and migration tests.
