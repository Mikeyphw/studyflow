# Phase 12 Acceptance

- Feature module `:feature:analytics` is present with `consumer-rules.pro`.
- Dashboard exposes `AnalyticsRoute` and `StudyAnalyticsScreen`.
- Data layer exposes `StudyAnalyticsRepository` without leaking Room into `:app`.
- Analytics cover study consistency, schedule adherence, accuracy, review backlog,
  retention, and weak content areas.
- Manifest inventory format is an integer and validation script SHA-256 is present.
