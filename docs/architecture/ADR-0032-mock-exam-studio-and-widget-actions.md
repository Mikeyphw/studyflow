# ADR-0032: Mock Exam Studio and Widget Actions

## Status

Accepted.

## Context

StudyFlow already stores questions and mock exams, and already publishes a Today widget. The next
learning-loop improvements should strengthen exam simulation and reduce friction from the launcher
without adding a cloud dependency or a schema migration.

## Decision

Add a domain `MockExamStudioPlanner` that returns preview-only blueprints. The workspace converts a
blueprint to a persisted `MockExam` only after the learner confirms the dialog.

Keep the existing `AppWidgetProvider` infrastructure and extend it with action pending intents. This
avoids a UI toolkit migration while preserving the manifest receiver, provider metadata, and debug
probe contracts already proven by earlier phases.

## Consequences

- Domain warnings stay enum-based so the UI can localize them.
- Mock exam creation remains rollback-safe because it reuses `ManageStudyPractice.createMockExam`.
- Widget launches reuse the same extras as reminder taps, so V2 navigation opens Today, Reviews,
  Schedule, or recovery contexts consistently.
- A future Glance migration can happen later as a dedicated widget-UI migration.

## r2 amendment

The Mock Exam Studio UI must expose the same filtering dimensions used by the planner. Board, source, tags, and disputed inclusion remain lightweight request values, not persisted wizard state.
