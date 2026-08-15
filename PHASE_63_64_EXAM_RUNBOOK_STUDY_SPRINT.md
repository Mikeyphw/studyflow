# Phase 63-64: Exam runbook and preview-first study sprint

## Scope

Phase 63 adds an exam-date-aware runbook to the existing Progress workspace. Phase 64 adds a
preview-first seven-day sprint planner. Both features remain local/offline and derive from the
existing Study Plan, analytics, readiness, schedule, review, practice and mock-exam evidence.

## Phase 63 — Exam runbook

`StudyExamRunbookPlanner` is a pure, read-only planner. It does not own repositories or persistence.
When a Study Plan has no exam date it returns an explicit `NO_EXAM_DATE` state rather than inventing
a countdown. With a date, it can surface bounded actions for overdue reviews, weak areas, practice
accuracy, mock rehearsal, schedule recovery and final review.

The Progress workspace maps those actions to existing Plan, Content, Practice, Reviews and Analytics
surfaces. Weak-area labels come from the current plan data; user-facing guidance remains localized
in the workspace resource layer. The planner fails closed if nested schedule strategy, rule or activity
evidence belongs to another plan, and bounded ordering keeps urgent review/final-review guidance ahead
of lower-priority advice when more than six actions are eligible.

## Phase 64 — Seven-day study sprint

`StudySprintPlanner` examines only existing unlocked `PLANNED` activities with scheduled starts in
the next seven days. It never creates new planned-activity IDs, edits locked or historical terminal
rows, or writes during preview. The preview is bounded to seven candidate activities.

The planner chooses one evidence-backed focus: review recovery, schedule recovery, practice-accuracy
repair, weakness focus, or balanced/no-change. Proposed changes keep activity identity and start time,
and may only rebalance the activity type or shorten its duration.

Apply is explicit and version-bound. `ManageStudySchedule.applyVersionedPreview` first validates the
Phase 64 mutation envelope: one target per activity, no moved starts, no status changes, no duration
increase, no locked targets, no executable no-ops and no more executable mutations than previewed
candidates. Apply re-reads the current schedule, requires a fresh preview, recomputes the bounded
seven-day candidate set and candidate minutes, and rejects any target outside that set. It then
delegates to the existing `StudyScheduleRepository.applyAdaptivePreview` transaction, which rechecks
the schedule version, current activity fields, lock/session ownership and candidate overlap/availability
before CAS mutations are committed.

## Recovery and lifecycle

The runbook and sprint preview are derived from repository-backed Progress inputs, so they are
recomputed after recreation rather than persisted as a second source of truth. Only apply-result UI
feedback is saveable. Sprint Apply has an ephemeral in-flight guard so repeated taps cannot enqueue
concurrent mutations. Coroutine cancellation is rethrown; ordinary `Exception` failures enter the
in-screen failure state, refresh repository-backed inputs, and can retry against the latest recomputed
preview without converting fatal `Error` subclasses into recoverable UI failures.

## Accessibility and localization

Both phases reuse the scrollable Progress `LazyColumn`, design-system timeline/action rows and minimum
touch-target behavior. New user-facing strings are present in English and pt-BR. Runbook items expose
one existing navigation action; sprint changes remain preview rows until the explicit Apply action.

## Regression coverage

- `StudyExamRunbookPlannerTest` covers no-date truthfulness, nested ownership and bounded urgent-action priority.
- `StudySprintPlannerTest` covers the seven-day/unlocked/existing-ID boundary, nested ownership,
  source immutability, schedule-version binding, the safe Apply mutation envelope and healthy no-op behavior.
- `scripts/verify_phase_63_64.py` checks production wiring, localization parity, the preview/apply
  boundary, and the historical Phase 62 onboarding boundary.
- `verifyPhase63_64ExamRunbookSprintSeal` is wired into `checkAll` and `verifyRepositoryContracts`.

This is an intermediate feature overlay. Android/Gradle/lint/device validation is intentionally not
claimed here and remains deferred to the campaign's final validation overlay.
