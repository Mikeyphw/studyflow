# ADR-0018: Settings, Backup Polish, and App Shell Seal

## Decision

Settings owns the app-shell readiness surface. Backup operations continue to live in the portability
center; Settings links to that center for the default Study Plan instead of duplicating backup logic.

## Consequences

- Snapshot and import/export behavior remains centralized.
- The app shell exposes language, theme, accessibility, and backup readiness in one place.
- Release polish can focus on hardening rather than adding another backup implementation.
