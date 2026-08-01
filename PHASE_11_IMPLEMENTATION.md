# Phase 11: Goals, Notes, and Progress Command Center

Phase 11 turns existing support/planning tables into user-facing goal and note workflows.

## Added

- `:feature:progress` Android module.
- `StudyProgressRepository` domain interface.
- `ManageStudyProgress` use case.
- `GoalProgressCalculator` summaries.
- `RoomStudyProgressRepository` and `StudyProgressEntityMapper`.
- DAO find/delete operations for goals and notes.
- Dashboard route and navigation to the progress command center.
- Phase 11 verifier and documentation.

## Schema

No Room migration is required. Phase 11 uses existing schema version 2 tables:

- `study_goals`
- `study_notes`
