# ADR-0011: Study Schedule and Strategy

## Status

Accepted for Phase 6.

## Context

A Study Plan needs more than a static calendar. It must support fixed weekly blocks, study cycles, goal-based planning, adaptive replanning and safe recovery from missed work. Phase 2 already introduced the planning tables, so Phase 6 should reuse them instead of adding a new schema migration.

## Decision

Schedule state is represented by three existing plan-owned entities:

- `StudyStrategy` stores the active planning mode and ratio configuration.
- `ScheduleRule` stores reusable weekly blocks and planning constraints.
- `PlannedActivity` stores generated or manually created study work.

The domain layer owns validation and deterministic generation through `ManageStudySchedule` and `StudySchedulePlanner`. The data layer owns transactional Room persistence through `RoomStudyScheduleRepository`. The app and feature modules depend only on domain-facing interfaces and never on Room.

Missed activity handling is explicit through `MissedActivityHandling`:

- postpone;
- redistribute;
- return to backlog;
- skip;
- keep unchanged.

Phase 6 ships `RETURN_TO_BACKLOG` in the UI and keeps the other policies in the domain model for the next adaptive-planning phases.

## Consequences

- No database migration is required for Phase 6.
- Locked activities are preserved by bulk generation and missed-work recovery.
- Generated activities can be replaced transactionally without touching locked manual work.
- Future adaptive scheduling can build on the same domain repository.
