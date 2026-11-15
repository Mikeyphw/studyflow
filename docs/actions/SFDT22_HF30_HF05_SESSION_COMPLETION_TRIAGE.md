# StudyFlow SFDT22 HF30 HF05 — Separate session write and UI completion evidence

## Evidence and uncertainty

Run 42945727812 on 6f24f3fc8fd3be7e558cb484e742da0b386334e7
failed within the third production journey at `session_completion_ui` with
`compose_wait_timeout`. Plan, subject/topic, two Room tests and three SFLP
instrumentation tests passed. An ambiguous combined UI wait could not show
whether `sessionManager.complete()` failed or the Compose session card stayed
stale after successful persistence. This patch does not assert either root
cause without runtime evidence.

## Hardening

The test now checks the real read-only SQLite status as the first *mandatory*
post-submit condition, retaining the strict completed-status, end-timestamp,
exact created plan/session identity and no-active-rows join. It watches the
localized production `operation_failed` feedback and refuses the operation
with fixed `session_finish_operation_failed`; a missing committed row remains
`session_status_not_completed`. After SQL proof passes, the real no-active
session card must be uniquely visible using `studyflow.session.active.empty`
and the pause/resume/finish actions must be absent; stale UI is categorized
as `session_ui_not_settled`. This is not a timeout increase or fallback.

## Gates and safety

The pre-existing five UI journeys, seven real screenshots, new process PID,
Room/SFLP regression tests, exact source pins and final hosted release gate
remain mandatory. The focused gate covers four journeys but is deliberately
nonqualifying. All failure categories are allowlisted, sanitized and contain
no private strings, raw logs, user data or database rows. PR #16 is the only
mutation target; staging and main are untouched. No merge is performed.
