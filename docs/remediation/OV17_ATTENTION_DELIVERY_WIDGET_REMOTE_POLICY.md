# OV17 — Attention delivery, widget resilience & remote policy

Overlay 17 closes S7-09 through S7-14 and DS2-40.

## Closure

- Widget refresh isolates each widget instance so one repository/configuration failure renders only that widget as data unavailable and does not consume the wakeup for the rest.
- Widget and reminder invalidation observers are self-healing process-lifetime collectors; transient flow failures are retried instead of killing observation permanently.
- Remote SFLP attention delivery applies the recipient Android ATT10 policy before posting, compact-rendering, silencing, or quiet-hours deferring.
- ATT10 quiet-hour timezone state refreshes from the current Android timezone after travel/timezone changes.
- Expired snooze rows are purged during reconciliation.
- Notification IDs use SHA-256-derived stable positive IDs instead of Java String.hashCode.
- Remote notification taps preserve canonical plan/type/target destination through StudyReminderLaunchActivity.

Validation is embedded in `verifyOv17AttentionDeliveryWidgetRemotePolicy` and the apply hook does not stop Gradle daemons.
