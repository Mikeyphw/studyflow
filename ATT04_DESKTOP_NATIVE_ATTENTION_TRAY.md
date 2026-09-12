# ATT04 — Desktop Native Attention + Tray Product

ATT04 promotes Desktop from protocol-only notification plumbing to a real local attention client built on ATT01's persistent attention ledger and ATT02's shared reminder planner.

## Delivered product slice

- A persistent Desktop reminder policy store with six shared reminder categories and Desktop-specific native notification, tray, pause, and master-enable preferences.
- A process-owned scheduler that performs startup/restart recovery immediately, then reconciles every 30 seconds while StudyFlow is running.
- Durable scheduled/delivered/snoozed/dismissed state in the shared ATT01 ledger, so process restarts do not replay handled reminders.
- Native AWT `SystemTray` delivery with capability detection and honest fallbacks on headless/unsupported systems.
- A tray mini-dashboard showing next study work, due reviews, active session state, progress, and paused status.
- Tray actions for Open Today, Start next, Review due, Snooze current reminder, Dismiss current reminder, Pause/resume reminders, and Open notifications/settings.
- Settings UI with runtime health, next reminder, last delivery, native/tray capability, recovery count, test notification, manual reconciliation, and all Desktop attention toggles.
- Clean application shutdown of the scheduler and tray integration.

## Background truth

ATT04 deliberately does not claim OS-daemon delivery. Desktop reminders work **while StudyFlow is running**. Launch-on-login/background startup is not enabled automatically and remains an explicit future product decision rather than a hidden persistence mechanism.

## Native limitations

AWT tray balloons do not provide portable inline notification action buttons across operating systems. ATT04 therefore provides click routing plus stable tray-menu actions and exposes `actionButtonsSupported=false` in capability evidence. Later OS-specific adapters can implement richer actions without replacing the Desktop attention runtime.
