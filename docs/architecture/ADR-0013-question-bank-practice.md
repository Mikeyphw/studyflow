# ADR-0013: Question bank and practice repository boundary

## Status

Accepted for Phase 8.

## Context

StudyFlow already had schema support for questions, practice tests and mock exams, but no user-facing
repository or feature module used those tables. The next product slice needs a question bank and a
practice cockpit without triggering a migration or exposing Room entities to UI code.

## Decision

Phase 8 introduces `StudyPracticeRepository` as a domain-facing boundary and implements it with
`RoomStudyPracticeRepository` inside `:core:data`. The app container receives only repository
interfaces and use cases. The new `:feature:practice` module depends on `:core:domain`,
`:core:model` and `:core:designsystem`, never on `:core:database`.

Question options and tags are encoded in the database mapper. The domain model receives plain
`List<String>` values, keeping UI and use-case code independent of persistence encoding.

## Consequences

- The practice feature can grow toward answer review, imports and analytics without another module
  boundary change.
- Existing Phase 4 package portability stays schema-compatible because no new tables or columns are
  added.
- A later phase can add per-question attempt history as an explicit schema migration.
