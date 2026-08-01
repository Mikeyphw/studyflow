# Phase 12: Analytics and Insights

Phase 12 adds the analytics layer on top of the existing StudyFlow data model.

## Added

- `:feature:analytics` Android module.
- `StudyAnalyticsRepository` and `ManageStudyAnalytics`.
- `RoomStudyAnalyticsRepository` for Room-backed aggregates.
- Consistency, schedule adherence, accuracy, retention, and weak-area insights.
- Dashboard analytics route and bilingual resources.
- Phase 12 verifier and acceptance document.

## Schema

No Room migration is required. Phase 12 reads existing schema version 2 tables.
