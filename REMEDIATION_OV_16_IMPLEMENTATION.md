# Remediation OV16 implementation

Overlay 16 of 30 implements the Android attention scheduler, quiet-hours and durable-wakeup remediation scope.

Closed findings:

- `S7-01` — snoozed/break reminders fired during ATT10 quiet hours are re-armed to the quiet-window end before they can be consumed.
- `S7-02` — ATT10 quiet-hours policy now normalizes candidates before Android scheduling instead of relying on the legacy planner's pre-policy quiet-hours shift.
- `S7-03` — `MIRROR_IF_UNHANDLED` creates a second durable wakeup at the configured mirror boundary.
- `S7-04` — the next future due review receives a durable due-review alarm candidate.
- `S7-05` — notification-denied local routes cannot be marked delivered to nonexistent compact surfaces and instead retry durably.
- `S7-06` — session-ending, session-completed and break-completed alerts use exact wakeups when exact alarms are available.
- `S7-07` — widget refresh planning preserves events less than 60 seconds away.
- `S7-08` — active-session widget freshness uses exact near-term alarms instead of the 15-minute freshness window.

The overlay includes app/core tests and `verifyOv16AttentionSchedulerDurableWakeups`; validation is embedded in artifact apply and does not stop Gradle daemons.
