# ADR-0016: Goals, Notes, and Progress Command Center

## Decision

Goals and notes are managed as a single command center because both are plan-scoped control surfaces:
goals define intended outcomes while notes capture reusable study context.

## Consequences

- Goals and notes stay behind domain-facing repositories.
- Existing `study_goals` and `study_notes` tables are reused without a schema migration.
- Later analytics phases can read from this layer without exposing Room to UI modules.
