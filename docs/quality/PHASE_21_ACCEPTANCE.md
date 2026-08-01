# Phase 21 Acceptance

- `StudyReminderPlanner` produces deterministic notification candidates.
- Quiet hours suppress immediate candidates.
- Reminders screen exposes notification permission readiness without `LocalContext` resource lookups.
- Android manifest declares `POST_NOTIFICATIONS` and the Today widget provider.
- The widget provider, layout, and appwidget metadata are present.
- Dashboard and Settings expose reminders entry points.
- No `.devtool.toml` mutation is required.
