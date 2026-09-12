# Attention Delivery Router

ATT07 makes Attention delivery explicit and debuggable. The router receives a canonical
`StudyFlowAttentionOccurrence`, a set of current device-capability snapshots, and optional ledger
state. It produces a route with one owner, zero or more bounded mirrors, a suppression reason, and
human-readable diagnostics.

## Routing rules

1. Ledger-handled entries suppress redelivery before any device scoring happens.
2. Explicit `targetDeviceIds` are honored before policy fan-out.
3. `LOCAL_ONLY`, `PRIMARY_DEVICE`, `ACTIVE_THEN_PRIMARY`, `PHONE_AND_DESKTOP`, and
   `ALL_CAPABLE` map directly from the ATT01 delivery policy enum.
4. Foreground TV banners are valid while the TV app/browser is active; TV closed-app alarms are not
   claimed.
5. Stale, muted, permission-blocked, or ledger-only devices are not owners.
6. Handoff uses `StudyFlowAttentionHandoffRequest` and must name distinct source/target devices.

## Surface inputs

- Android: alarm + system notification + widget/badge.
- Desktop: system notification + tray while the process is running.
- Web/PWA: Web Push if subscribed, browser notification if granted, app badge/in-app center.
- Android TV / Web TV: foreground banner + ledger-only fallback.

The protocol-v1 capability fixtures remain stable; ATT07's device capability model is a runtime
projection layered on top of negotiated SFLP peer descriptors.
