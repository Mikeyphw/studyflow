# Phase 6: Schedule and Study Strategy

Phase 6 adds schedule and study-strategy management on top of the existing Phase 2 planning tables.

## Added

- `StudySchedule`, schedule generation results and missed-work recovery models.
- `StudyScheduleRepository` and `ManageStudySchedule`.
- Deterministic `StudySchedulePlanner`.
- Room-backed `RoomStudyScheduleRepository`.
- Planning entity mappers for strategies, rules and planned activities.
- DAO operations for observing, creating, deleting, generating and updating planned work.
- `:feature:schedule` with bilingual Compose UI.
- Dashboard route and menu entry for the schedule screen.
- Focused tests for schedule generation and UI labels.

## Warning cleanup

The unused resources reported after the Phase 4+5 bridge are removed from both English and pt-BR subject resource files:

- `sort_content`;
- `clear_exam_stage`;
- `difficulty_value`;
- `minutes_value`;
- `invalid_number`;
- `required_name`.

## No schema migration

Phase 6 reuses existing `study_strategies`, `schedule_rules` and `planned_activities` tables, so the Room schema remains version 2.
