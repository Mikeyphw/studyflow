# Phase 29B Reminder, Time, and Backup Hardening

Phase 29B is a focused release-candidate hardening overlay after the Phase 29 reminder-delivery grace hotfix.

## Delivered

- Replaced UTC epoch-day reminder math with local-time day math for daily summaries and Today widget planned counts.
- Kept the 15-minute inexact `AlarmManager` delivery window while adding a bounded two-hour late-delivery grace for planned-activity reminders delayed by Doze, App Standby, or wall-clock adjustments.
- Added notification-tap routing: due-review reminders open Reviews, planned/missed activity reminders open Schedule, and daily summaries open Dashboard for the target Study Plan.
- Reconciles reminders after notification permission is granted from the Reminders screen.
- Reconciles reminders after boot, manual time changes, timezone changes, date changes, and app package replacement.
- Requires encrypted cloud-backup transport capabilities for Auto Backup cloud backups while keeping device-transfer rules intact.

## Guardrails

- Late planned reminders inside the grace window do not rearm into a loop after delivery.
- Very old planned reminders outside the bounded late-delivery window remain suppressed.
- Quiet hours and inactive-plan suppression remain enforced by the runtime delivery recheck.
- The overlay does not add exact-alarm permission, network providers, OAuth, Firebase, or cloud-sync claims.
