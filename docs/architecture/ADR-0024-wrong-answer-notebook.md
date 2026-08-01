# ADR-0024: Wrong-answer notebook without a schema bump

## Decision

Phase 20 implements the wrong-answer notebook using the existing `error_entries` and `review_items` tables.
Repeated mistakes update the existing error entry for the same question and increment its recurrence count.
Resolving an error deletes the entry and linked error-based review items.

## Rationale

The Phase 2 schema already modeled error entries and review links. Reusing those tables keeps Phase 20
small and avoids a migration while still delivering automatic capture, recurrence tracking, and remediation.

## Consequences

A later richer error history phase may add immutable attempt/error events. The active notebook remains simple:
what exists is active, and resolved entries are removed from the active queue.
