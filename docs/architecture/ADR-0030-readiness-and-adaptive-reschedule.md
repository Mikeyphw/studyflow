# ADR-0030: Exam Readiness and Adaptive Reschedule

## Status

Accepted.

## Context

After the UI/UX V2 migration, the Progress workspace can show study pace and weak areas, but the
learner still needs a single explainable answer to two questions: whether the current plan is exam-safe
and what should change when it is not.

## Decision

StudyFlow adds an explainable domain readiness score and a preview-first adaptive reschedule planner.
The readiness score is rebuilt from persistent plan, analytics, schedule, and practice data. It combines
coverage, review backlog, question accuracy, mock evidence, consistency, schedule adherence, weak
areas, and exam-date feasibility. No score is stored as durable truth.

The adaptive reschedule planner produces a `AdaptiveReschedulePreview` before mutation. It may update
only existing unlocked activities and must never edit locked blocks. Preview-only advice remains visible
but is not applied. The full schedule manager continues to own complex manual edits.

## Consequences

- Progress becomes a decision surface rather than a passive report.
- Readiness remains auditable because each signal is visible.
- Missed work recovery is safe, bounded, and reversible.
- The app avoids silent plan rewrites that would erode trust.
