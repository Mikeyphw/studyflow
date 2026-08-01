# Phase 21: Reminders, Notifications, and Study Widgets

Phase 21 closes the reminders/widgets gap from the post-MVP audit.

## Delivered

- Deterministic reminder planner for due reviews, planned activities, missed recovery, and daily summaries.
- Reminders center UI with permission status, quiet hours, saved per-plan review notification preference, notification preview, and Today widget preview.
- Android 13+ notification-permission request path guarded by SDK checks and string-literal permission use.
- Android `POST_NOTIFICATIONS` manifest permission.
- AppWidgetProvider, widget metadata, and Today widget layout.
- Dashboard and Settings entry points.
- Phase 21 verifier.

## Not in scope

- Exact alarms and background scheduling services.
- Remote push notifications.
- Network sync for widget data.
