# Phase 25 Gap Closure Seal

## Phase 21 delivery closure

Phase 21 introduced reminder planning, permission UX, and a widget preview. Phase 25 closes the delivery
gap by adding app-local scheduling and notification posting:

- notification channels are created during application startup;
- each enabled active plan is reconciled into inexact reminder alarms;
- reminder broadcasts use `goAsync()` and re-read the database before posting;
- stale work is suppressed when the plan is paused, archived, trashed, deleted, completed, inside quiet
  hours, or when notification permission has not been granted;
- boot recovery reconciles reminders after restart;
- the Today widget is driven by a live `TodayWidgetSummary`.

## Phase 24 provider gate

The historical sync foundation remains local checkpoint based. Phase 25 does not add a provider, account identity, network endpoint,
OAuth surface, remote merge loop, or token persistence. The Phase 24 provider gate remains in force.

## Release posture

The release readiness seal now reports phases 1 through 25 and includes the post-MVP feature set. The seal is
source-level and contract-level only until Devtool completes Gradle configuration, compilation, lint,
resource processing, package assembly, and target-device smoke checks.

## Phase 25 r2 correction

The gap-closure seal now explicitly requires every ordinary reminder candidate, including the daily summary, to honor quiet hours. Immediate due-review alarms are not automatically rescheduled after notification delivery, preventing a one-minute notification loop when reviews remain due.
