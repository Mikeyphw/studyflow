# ADR-0019: Polish, Accessibility, and UX Consistency

## Decision

StudyFlow keeps the MVP polish pass as a domain-level readiness audit surfaced from Settings,
rather than scattering release-readiness booleans through individual feature modules.

## Rationale

The app is offline-first and feature-rich. A central polish audit keeps accessibility,
localization, empty/error/loading states, action feedback, and navigation consistency visible
without adding another persistence layer.

## Consequences

- No database migration is required.
- Polish gates are easy to verify in scripts and unit tests.
- Later release work can add dynamic checks while preserving the same domain contract.
