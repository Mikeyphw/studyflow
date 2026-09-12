# ATT10 — Quiet Hours / Focus Profiles / Escalation Policy

ATT10 adds a shared, auditable attention-delivery policy layer on top of the ATT01 canonical ledger and ATT07 delivery router. It changes **how and where** an existing reminder is delivered; it does not create a second reminder store and it does not delete a reminder merely because delivery is deferred or quieted.

## Delivered scope

- Shared Kotlin `StudyFlowAttentionFocusPolicy` domain and evaluator in `:core:link`.
- Timezone-aware quiet windows with overnight support and explicit next-quiet-end calculation.
- Profiles: Balanced, Exam mode, Work mode, Sleep mode, Travel/offline, and Minimal/TV.
- Per-category escalation settings for planned study, reviews, missed activity, daily summary, session events, exam countdown, schedule changes, plan warnings, and sync attention.
- Explicit outcomes: `DELIVER_NOW`, `DEFER_UNTIL_QUIET_END`, `SILENT_DELIVERY`, `COMPACT_ONLY`, `ROUTE_TO_PHONE`, `ESCALATE_MIRROR`, `SUPPRESS_DUPLICATE`, and `REQUIRE_USER_ACTION`.
- ATT07 `routeWithPolicy` integration. ATT10 may narrow eligible devices or mirror count; it cannot expand a bounded ATT07 route into an unbounded broadcast.
- ATT01 `policyAudit` metadata records the evaluated profile, outcome, timestamp, deferral target, and explanation on the canonical ledger entry.
- Android focus-policy persistence and Reminders UI controls; Android notification delivery honors defer/silent/compact decisions.
- Desktop focus profile/quiet-hours settings and policy evaluation before native delivery.
- Web/PWA policy controls, diagnostics, policy-aware router/runtime behavior, and runtime/dist/service-worker parity.
- Android TV and Web TV expose read-only / remote-safe policy projections and remain foreground-only attention clients.

## Delivery invariants

1. ATT01 remains canonical attention truth. Focus settings are configuration; they are not a reminder ledger.
2. A deferred reminder remains scheduled with the same stable occurrence identity and an audited ATT10 decision.
3. Duplicate suppression happens before focus-profile rules.
4. `ESCALATE_MIRROR` is bounded by both the ATT10 `maxEscalationMirrors` value and ATT07 routing constraints.
5. `COMPACT_ONLY` can only choose devices that actually advertise a compact `WIDGET_OR_BADGE` surface.
6. TV-class clients never become background alarm owners. Minimal/TV policy prefers a phone, while TV can still show a foreground banner when an existing route legitimately targets it.
7. Web does not claim closed-browser delivery. Optional Web Push remains the only browser background-delivery foundation and is advertised truthfully by capability state.

## Profile intent

| Profile | Intent | Typical delivery effect |
| --- | --- | --- |
| Balanced | Normal daily use | Category defaults; bounded overdue mirrors for study/review/missed attention |
| Exam mode | Protect exam-critical work | Exam countdown requires action; study/review may escalate when unhandled |
| Work mode | Reduce interruption | Summary/schedule/warning attention is quieter while study-critical items retain normal behavior |
| Sleep mode | Preserve reminders without waking the user | Non-bypassed attention defers to the end of quiet hours |
| Travel/offline | Favor resilient, low-noise surfaces | Compact-only projection where supported |
| Minimal/TV | Avoid treating TV as a notifier hub | Route to phone; TV projection is read-only/remote-safe |

## Quiet-hour semantics

`StudyFlowAttentionQuietWindow` stores local start/end minute plus an optional IANA timezone ID. A start later than the end represents an overnight range, for example 22:00–07:00. Kotlin uses `java.time.ZoneId`; Web uses `Intl.DateTimeFormat` with the configured timezone and scans wall-clock minute transitions to remain safe across overnight and DST boundaries. Equal start/end values are rejected/normalized because they are ambiguous.

High-priority and time-sensitive reminders may be configured to bypass quiet hours. A category can additionally opt into quiet-hour bypass. The evaluator records whether quiet hours were active even when the resulting outcome is not a normal notification.

## Platform behavior

### Android

The Reminders surface exposes focus profile, bypass switches, quiet-window controls, and per-category escalation modes. `StudyReminderDeliveryCoordinator` evaluates ATT10 before posting a notification. Deferred reminders are rescheduled, silent notifications use the platform silent path, and compact-only delivery refreshes canonical/widget state without fabricating a system notification.

### Desktop

Desktop stores focus configuration next to its existing reminder preferences. The runtime evaluates each due ATT01 entry before native delivery. Profile and quiet-hours controls are shown in Desktop Settings, together with the latest policy outcome. Desktop continues to state that reminders are available only while StudyFlow is running.

### Web / PWA

The Notification Center exposes the focus profile, quiet window, timezone, and per-category escalation selector. The TypeScript ATT10 evaluator mirrors the Kotlin outcome model. Decisions are recorded in the browser ATT01 ledger entry. Closed-browser delivery is not guaranteed; Web Push is capability-gated and optional.

### Android TV / Web TV

TV receives a read-only policy projection. It can surface the current profile/outcome and use remote-safe actions, but it does not gain new permissions or a fake background scheduler. Native Android TV and Web TV continue to report foreground-only attention delivery with designated-notifier/phone fallback.

## Verification

Focused gate:

```text
verifyAtt10QuietHoursFocusProfilesEscalationPolicy
```

The contract verifier is `scripts/verify_att10_quiet_hours_focus_profiles_escalation_policy.py`. It checks shared policy/domain wiring, ATT01 audit integration, ATT07 policy routing, Android/Desktop/Web/TV projections, docs, Web runtime/dist parity, and the focused Web regression tests. Gradle unit-test dependencies cover core/link, Android app, Desktop, and Android TV when the target machine can resolve the configured Gradle distribution/dependencies.

## Non-goals

- No guarantee of closed-browser or closed-TV delivery.
- No new Android/TV permission solely for ATT10.
- No second attention truth store.
- No unbounded all-device escalation.
- No replacement of ATT01 occurrence identity or ATT07 routing semantics.
- No claim that a source-contract pass substitutes for final ATT12 release validation.

### Upgrade and legacy quiet-hours compatibility

ATT10 does **not** invent a 22:00–07:00 quiet window on upgrade. A new focus policy starts with quiet hours off unless the existing Study Plan already has a configured quiet-hours interval. Android migrates and saves the legacy interval when ATT10 is first loaded; its Reminders screen saves the legacy planner fields and ATT10 policy together. Desktop performs the same one-time migration and updates the legacy planner fields when its ATT10 quiet-hours toggle changes. This prevents the pre-ATT10 scheduler and the ATT10 delivery evaluator from becoming two independent quiet-hours truths.

