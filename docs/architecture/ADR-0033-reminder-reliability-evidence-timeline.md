# ADR-0033: Reminder reliability and evidence timeline

## Status

Accepted.

## Context

StudyFlow already schedules reminder candidates, exposes a Today widget, records sessions, reviews,
questions, tests, goals, and notes, and keeps a privacy-preserving diagnostic recorder. The next UX
step is to make reminder reliability understandable to the learner and make progress evidence
traceable without adding cloud sync or account dependencies.

## Decision

Reminder reliability is modeled as a domain inspection, not as optimistic UI copy. The report keeps
notification permission, exact-alarm access, battery optimization, quiet hours, backlog pressure, and
candidate availability separate so the UI can explain what is blocked, degraded, or ready.

The evidence timeline is also domain-derived. It accepts existing repository snapshots and emits a
bounded newest-first list of typed evidence events. The event kind is localized by the UI; event labels
come only from learner-created titles or statements. This avoids hard-coded English prose in the
domain layer and preserves pt-BR parity.

## Consequences

No database migration is required. The feature is offline-first, source-testable, and can later be
fed by the diagnostic recorder or backup/restore audit trail without changing the Progress workspace
surface contract.
