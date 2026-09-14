# OV16 — Attention scheduler, quiet-hours & durable wakeups

OV16 moves Android reminder wakeup decisions behind an ATT10-first contract so reminder alarms are not consumed unless there is a visible or durable outcome.

## Product changes

- `StudyReminderWakeupContracts` centralizes Android wakeup decisions for ATT10 quiet-hours deferral, mirror escalation boundaries, permission-denied retry, exact session/break alarms, and near-term widget refreshes.
- `StudyReminderDeliveryCoordinator` now normalizes planner candidates through ATT10 before scheduling, re-arms fired snooze/break candidates that land inside quiet hours, retries notification-denied deliveries instead of marking them delivered, and schedules a second wakeup for `MIRROR_IF_UNHANDLED` occurrences.
- `StudyReminderPlanner` emits a future due-review candidate for the next upcoming review item so the device has a durable wakeup before the review becomes due.
- `AndroidStudyFlowAttentionRouterBridge` no longer advertises notification or compact-widget surfaces unless the device can actually deliver to them.
- Session-ending, session-completed and break-completed reminders use exact wakeups when the platform permits exact alarms, with bounded fallback when exact alarms are denied.
- `StudyFlowWidgetSnapshotProjector` preserves sub-minute domain boundaries and `StudyFlowWidgetFreshnessScheduler` uses exact near-term refreshes instead of a 15-minute window.

## Validation

The artifact validation hook runs `verifyOv16AttentionSchedulerDurableWakeups`. That Gradle task owns native compilation/tests and invokes `scripts/validate_ov16_attention_scheduler_wakeups.sh`, which runs the source-evidence verifier. The hook does not stop Gradle daemons.
