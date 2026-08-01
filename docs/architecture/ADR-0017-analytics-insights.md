# ADR-0017: Analytics and Insights

## Decision

StudyFlow analytics are computed from existing plan-owned tables rather than a new persistence layer.
The analytics repository lives behind `:core:data`; the UI receives domain snapshots through
`ManageStudyAnalytics`.

## Consequences

- No schema migration is required for Phase 12.
- Insights stay reproducible from source-of-truth records.
- Future phases can cache expensive analytics without changing the public domain API.
