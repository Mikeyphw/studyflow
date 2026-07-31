# ADR-0007: Study Plan lifecycle and complete graph duplication

## Status

Accepted for Phase 3.

## Context

A Study Plan is the ownership root for every record associated with one study objective. Plan
management therefore cannot be implemented as cosmetic status labels or by copying only the root
row. Editing, duplication, archive, trash, restoration, and permanent deletion must preserve the
ownership boundary established in Phase 2.

## Decision

- Lifecycle state remains an English domain enum: `DRAFT`, `ACTIVE`, `PAUSED`, `COMPLETED`,
  `ARCHIVED`, and `TRASHED`.
- Moving to trash records `trashedAtEpochMillis`; restoring from trash returns the plan as `PAUSED`
  so notifications and future schedule execution do not restart unexpectedly.
- Permanent deletion uses the existing Room cascade transaction.
- Duplication inserts a new root with `DRAFT` status and copies all twenty-one plan-owned child
  tables inside one database transaction.
- Copied child identifiers use `<new-plan-id>:<old-id>`. Every copied foreign key uses the same
  transformation, keeping the duplicate graph internally closed.
- Plan-specific settings are created with the root, copied with the graph, and exposed through the
  repository boundary.
- The optional default Study Plan is an application preference stored in DataStore, not a property
  of the plan. This prevents a plan export from silently changing another installation's launch
  behavior.

## Consequences

- Two duplicated plans can evolve independently at the database level.
- A clone starts as a draft even when the source is active.
- Trashed and archived plans are excluded from normal dashboard switching.
- Linked external documents may intentionally retain the same user-selected URI. Private annex
  byte-copy policy remains owned by the later annex-storage phase.
- Future schema additions must extend the graph duplicator and the Phase 3 table-coverage test.
