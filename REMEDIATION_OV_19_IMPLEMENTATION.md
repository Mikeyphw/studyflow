# Remediation OV19 — Native TV practice, mock, review & schedule truth

Overlay 19 of 30 hardens Android TV execution semantics so TV controls remain presentation-safe while every mutation maps to a real canonical phone/domain operation.

## Findings closed

- `S8-04` — Native TV makes only the first three of a 20-question practice projection reachable.
- `S8-05` — TV exposes synthetic/completed practice sets as interactive even when they cannot persist.
- `S8-06` — Session/mock elapsed-time presentation freezes without repository emissions.
- `S8-11` — Mock Exam Studio projects aggregate stats, not real question/section execution.
- `S8-12` — Mock pause/resume is exposed but does not persist/change canonical state.
- `S8-13` — New ready mock can be hidden behind older submitted mock.
- `S8-14` — Mark known and complete review collapse to the same grade mutation.
- `S8-15` — TV schedule displays raw epoch milliseconds as a time label.
- `DS2-45` — Review/schedule item open commands use TV slot IDs instead of canonical source IDs.

## Implementation

- Expanded the native TV practice question slot model to 20 reachable slots and preserved canonical question source IDs in command targets.
- Added persistability metadata to practice projections so not-started/submitted/synthetic practice sets do not expose answer or finish mutations.
- Added projection timestamps and live elapsed calculations for session and mock cast state.
- Extended mock exam presentation protocol with bounded real sections, section progress, current section identity, and persistability.
- Added canonical mock pause/resume domain mutations backed by attempt metadata and `updateMockExamAttemptIfOpen`.
- Updated mock projection selection to prefer running/open/ready exams over stale submitted history.
- Distinguished review `known`, `complete`, and lower-grade review intents before mapping to canonical review grades.
- Formatted schedule times through local `DateTimeFormatter` and preserved canonical schedule activity IDs in open commands.
- Removed review/schedule reducer fallbacks that treated TV enum slot IDs as source IDs.

## Validation

`verifyOv19NativeTvPracticeMockReviewScheduleTruth` runs native TV, app, core-domain, and core-tvcontrol tests, then executes the source-evidence verifier.
