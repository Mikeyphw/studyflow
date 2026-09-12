# ATT06 — Android TV + Web TV Attention Experience

ATT06 completes the TV attention layer for StudyFlow without pretending that a television is a reliable background alarm device.

## Delivered scope

- Android TV foreground-only attention runtime backed by the ATT01 durable ledger.
- D-pad-safe, non-blocking banner model with Open, Continue, Snooze, Dismiss, Review, Start and Move-to-phone actions.
- Android TV settings truth: foreground banners are available while the app is open; closed-TV delivery requires the designated phone/desktop/Web notifier that ATT07 will arbitrate.
- Web TV foreground attention projection on top of the ATT05 Web/PWA runtime.
- Web TV remote-safe action projection, route mapping, capability data attributes and browser capability truth.
- Regression coverage for Android TV banner routing, unsupported background claims, runtime ingest/acknowledgement, Web TV projection and generated runtime parity.

## Product truth

TV is a foreground display and control surface in ATT06. It is not the default background notification hub, does not claim exact alarms, and does not silently schedule closed-app reminders. The TV surfaces show attention while open and move background delivery back to a designated notifier when the TV cannot guarantee delivery.

## Validation

Focused gate: `verifyAtt06TvAttentionExperience`.

The gate verifies Android TV source, Web TV source/runtime parity, documentation, capability-matrix truth, Devtool task wiring, and ATT01–ATT05 regression contracts.
