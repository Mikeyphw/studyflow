# Phase 20: Wrong-Answer Notebook and Remediation Loop

Phase 20 closes the practice-to-remediation loop.

## Delivered

- Automatic wrong-answer capture when a practice test or mock exam is finished.
- Recurrence-aware error entries keyed by question.
- Wrong-answer notebook section in the Reviews screen.
- Error review scheduling and resolve/delete flow.
- `WrongAnswerRemediator` priority scoring and remediation suggestions.
- DAO and repository accessors for recurring question errors.
- Phase 20 verifier wired into `scripts/validate_repo.sh`.

## Schema decision

No Room migration is required. Phase 20 uses the existing `error_entries` and `review_items` tables and
adds DAO queries around them. Resolving an error deletes its notebook entry and linked error-based review
items so resolved mistakes leave the active notebook without adding status columns.
