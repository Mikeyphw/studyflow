# Phase 19: Practice/Test/Mock Execution Engine

Phase 19 closes the gap between question-bank metadata and real practice execution.

## Delivered

- Persisted answer ledger for `StudyTest` and `MockExam` attempts.
- Start/resume flows for tests and mocks.
- Per-question answer capture and correctness calculation.
- Finish flow that updates answered/correct counts and mock score percentage.
- Interactive practice runner on the Practice screen.
- Phase 19 verifier wired into `scripts/validate_repo.sh`.

## Storage decision

The existing schema already had `study_tests` and `mock_exams` with `configurationJson`, so Phase 19
stores the ordered question IDs and answer ledger in that payload. This avoids a migration while still
making attempts restart-safe and testable.
