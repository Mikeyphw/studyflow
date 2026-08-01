# Phase 20 Acceptance

- Finishing practice tests and mock exams captures wrong answers into `error_entries`.
- Repeated mistakes for the same question increment recurrence count.
- Reviews exposes a wrong-answer notebook with schedule-review and resolve actions.
- Error-based review items are linked to notebook entries.
- Resolving an error removes linked error-based review items.
- `scripts/verify_phase_20.py` is wired into `scripts/validate_repo.sh`.
