# ATT05 — Web/PWA Notification & Attention Runtime

ATT05 makes the normal Web client a first-class StudyFlow Attention client. The Web app now owns a production `StudyFlowWebAttentionRuntime`, browser/PWA capability detection, service-worker notification lifecycle handling, foreground scheduling, in-app Notification Center, optional Web Push subscription foundation, app-badge progressive enhancement, and durable ATT01 ledger convergence.

## Product scope

- User-initiated browser notification permission flow.
- Foreground reminders for planned study, reviews, missed activities, daily summaries, session-ending warnings, session completion, and exam countdowns.
- Service-worker `push`, `notificationclick`, `notificationclose`, and pending-event drain handling.
- In-app Notification Center in the normal Web shell.
- Category-specific Web reminder controls.
- Timing controls for study lead, missed-delay, session warning, and daily summary minute.
- Optional Web Push subscription foundation with an explicit VAPID/application-server-key input.
- App-badge update and clear as a progressive enhancement.
- SFLP notification delivery ingestion into the same durable ATT01 browser ledger.
- Clear product truth: without Push, closed-browser delivery remains foreground-only/best effort.

## Compatibility

ATT05 does not change TV browser behavior. Web TV remains a foreground ten-foot client and does not claim reliable closed-browser delivery. ATT06 owns TV attention banners.

## Verification

Focused gate: `verifyAtt05WebPwaAttentionRuntime`.

The gate checks the source/runtime/dist PWA contract, service-worker lifecycle, React Notification Center, Web Push foundation, browser capability truth, SFLP/ATT01 ledger convergence, Web regression tests, and capability-matrix documentation.
