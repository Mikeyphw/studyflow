# Web/PWA Attention Runtime

ATT05 promotes StudyFlow Web from protocol-only notification plumbing to a real product surface.

## Delivery model

StudyFlow Web supports two delivery modes:

1. **Foreground/local runtime** — when the Web app is open, `StudyFlowWebAttentionRuntime` scans local StudyFlow state and schedules Attention occurrences in the durable browser ledger.
2. **Service-worker notification path** — when Push is configured and the browser delivers a push event, the service worker records the occurrence, shows a browser notification, and drains the event back into the app ledger on the next client connection.

Without Web Push infrastructure, StudyFlow Web does **not** promise arbitrary closed-browser alarms. The UI reports this as `foreground-only`.

## User controls

The Notification Center exposes:

- permission and capability status;
- service-worker state;
- Push subscription state;
- app-badge support;
- category toggles;
- timing controls;
- upcoming and recent occurrences;
- Open, Snooze, and Dismiss actions.

## Ledger semantics

Foreground reminders, remote SFLP deliveries, service-worker pushes, notification clicks, closes, snoozes, and dismissals all converge on the ATT01 `StudyFlowAttentionLedgerSnapshot`. This prevents Web from inventing a second reminder history and keeps duplicate suppression compatible with Android and Desktop.

## Progressive enhancement

Browser notifications, Push, and app badges are optional capabilities. The in-app Notification Center remains available even when browser notification permission is denied or unsupported.
