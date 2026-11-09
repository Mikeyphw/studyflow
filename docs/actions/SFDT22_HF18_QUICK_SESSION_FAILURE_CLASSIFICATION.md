# StudyFlow SFDT22 HF18 — quick-session start failure classification

## Audit evidence and distinction

PR #15 at `daaa3b06569103d761191853092803787010aa70`, GitHub run `42529249417`:
SFDT11, SFDT21, Devtool CI and StudyFlow CI passed; SFDT22 compiled production and
instrumentation APKs and executed two journeys successfully. Session journey failed
at `session_running` with `session_start_failed` after production `operation_failed`
feedback. The observed failure comes from a real app operation, but its exception
has not been identified; no data-layer fix is justified by this evidence alone.

## Scoped, non-invasive telemetry

`ManageStudySession.startQuickSession` now has an OPTIONAL failure-category callback,
so all existing callers and exception contracts remain unchanged. The enum signals:

- `VALIDATION`: rejected input or duration validation
- `PLAN_SETTINGS`: plan settings lookup or required settings missing
- `ACTIVE_LOOKUP`: failed query looking for any already-active session
- `ACTIVE_SESSION`: a genuine already-active session prevents a second one
- `PERSISTENCE`: repository createSession call rejected or failed (this includes
  ownership validation AND database insertion; the category DOES NOT claim which)

Cancellation still propagates unchanged. The production UI retains its generic
user-visible error wording. Only a fixed, semantic `testTag` on the Snackbar host
signals the precondition stage, without IDs, exception messages or raw logs. The
tag is cleared before each quick-session start.

The HF17 test still requires a unique, visible, enabled real Pause button for a
successful RUNNING state. If the production failure Snackbar appears, the hosted
test first checks the fixed category tag and fails with an allowlisted sanitized
reason. Unknown cases remain generic `session_start_failed`. No fake repository
writes, seeded fixture, success override, retries or skip is introduced.

## Hosted qualification

Exact SHA-256 source pins, source-only HF18 tests, and the previous HF01–HF17
contracts remain active. Five MainActivity UI journeys, seven distinct screenshots,
verified new process PID on cold restart, API 36 x86_64 emulator and release/signing
boundaries are all unchanged. No local Gradle. PR is not qualified for merge until
the hosted gate passes. This hotfix improves failure classification; it does not
assert that the underlying session-start problem is fixed.
