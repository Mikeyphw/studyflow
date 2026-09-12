# Desktop Attention Runtime

StudyFlow Desktop owns a native attention runtime that consumes the shared reminder planner and persists occurrence lifecycle in the ATT01 persistent attention ledger.

## Scheduling and recovery

The runtime reconciles immediately on startup and every 30 seconds while the StudyFlow Desktop process is alive. It evaluates the current default plan, schedule, review queue, active sessions, quiet hours, daily summary policy, and exam countdown policy using the same `StudyReminderPlanner` used by Android. A restart reloads ledger state before reconciliation, preventing already handled occurrences from being delivered again.

## Native notification adapter

The current portable adapter uses Java AWT `SystemTray` / `TrayIcon`. It reports whether native notification and tray APIs are actually available. Unsupported/headless environments keep canonical reminder state and settings working without pretending that a system notification was displayed.

AWT does not expose consistent cross-platform inline notification **action buttons**. ATT04 therefore uses notification click routing plus tray actions. The port already models capability state so future Windows/macOS/Linux-specific notification implementations can add rich action buttons without changing domain scheduling.

## Tray mini-dashboard

The tray tooltip/menu provides next study work, due review count, active session state, plan progress, and paused status. Commands include Start next, Review due, Snooze current reminder, Dismiss current reminder, Pause/resume, Open Today, and Open notifications/settings.

## Persistence

`attention-preferences-v1.properties` stores Desktop delivery preferences and per-plan reminder policy. `attention-ledger.json` remains the shared durable ATT01 lifecycle store. Snooze creates a new scheduled occurrence linked to the original rather than erasing delivery history.

## Background behavior

Desktop notifications are available while StudyFlow is running. ATT04 does not silently install an autostart agent or daemon. If launch-on-login is added later it should start this same runtime rather than create a second scheduler.
