# Phase 29 Reminder Delivery Grace Hotfix

This hotfix closes the planned-activity reminder delivery gap found after Phase 29.

## Problem

StudyFlow schedules planned-activity reminders with inexact `AlarmManager.setWindow`. If Android delivers the alarm after the planned start time, the fresh-state recheck could rebuild the reminder plan, drop the planned-activity candidate as already past, and return without posting the notification.

## Fix

- Adds a shared `StudyReminderPlanner.PlannedActivityDeliveryGraceMillis` constant matching the inexact alarm window.
- Allows planned activities to remain valid during that grace window.
- Keeps stale planned activities outside the window suppressed.
- Keeps quiet-hours suppression intact.
- Keeps completed activities suppressed even inside the grace window.
- Reuses the domain constant from the Android delivery coordinator so scheduling and runtime recheck stay aligned.

## Validation

- `StudyReminderPlannerPhase29HotfixTest` covers late in-window delivery, stale alarm suppression, quiet-hours suppression, and completed-activity suppression.
- `verify_phase_29.py` now fails if the grace helper, shared alarm-window constant, or hotfix tests are missing.
