# ADR-0012: Study Session Execution

## Status

Accepted.

## Context

The application already stores planned activities and schedule rules. Learners also need a
lightweight execution loop: start, pause, resume, finish, and review study sessions.

## Decision

Study sessions are represented by the existing `study_sessions` table and exposed through a
domain repository. The UI never writes Room entities directly. Timer arithmetic is handled by
`StudySessionTimer`, allowing repository implementations and tests to share the same behavior.

Planned activity state changes are performed in the Room repository transaction that saves the
linked session. This avoids a state where a timer exists without the corresponding planned
activity transition.

## Consequences

- Phase 7 does not require a database migration.
- Only one running or paused session is allowed per Study Plan.
- Completion captures pages, questions, correct answers, ratings, and notes.
