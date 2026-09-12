# ATT02 — Android Attention System 2.0

ATT02 promotes Android from the legacy reminder implementation to the reference client for the shared ATT01 Attention domain. The overlay is intentionally a full product slice: policy, planning, AlarmManager delivery, recovery, actions, deep links, diagnostics, UI, localization, persistence/migration, tests, and Devtool validation ship together.

## Production behavior

### Reminder categories

Android exposes independent per-plan controls for:

- planned study blocks;
- due reviews;
- missed activities;
- daily summaries;
- session timers;
- exam countdowns.

`AndroidStudyReminderPolicyStore` is a versioned per-plan store. On first read it migrates the existing `StudyPlanSettings.reviewNotificationsEnabled` value and then persists the complete ATT02 policy. Quiet hours remain in `StudyPlanSettings` because they are already canonical plan settings.

### Timing

The default daily summary is 08:00 local time, replacing the previous implicit next-midnight behavior. Users can configure planned-block lead time, summary time, missed-activity delay, default snooze, session-ending lead time, exam countdown horizon, and quiet hours.

### Scheduling and recovery

`StudyReminderDeliveryCoordinator` remains deliberately based on `AlarmManager.setWindow`: StudyFlow does not require exact-alarm permission for ordinary study reminders. It schedules all policy-enabled candidates and reconciles after boot, wall-clock/time-zone/date changes, package replacement, app resume, policy changes, and live schedule/review/session/settings invalidation.

Permission loss removes platform alarms without terminally cancelling the semantic ATT01 occurrence, so granting permission later can restore delivery. Removed or invalid domain occurrences are cancelled normally.

### Channels

ATT02 creates four Android channels:

1. Study schedule (`study_schedule`)
2. Reviews (`study_reviews`)
3. Session timers (`study_session_timers`)
4. Summaries (`study_summaries`)

The pre-ATT02 `study_reminders` channel is deleted after the replacement channels are created.

### Notification actions

- Due reviews: **Review now**, **Snooze**
- Planned study: **Start now**, **Snooze**, **Reschedule**
- Missed activity: **Reschedule**, **Mark done**
- Daily summary: **Open today**
- Session ending: **Continue**, **Snooze**
- Session target reached: **Finish**, **Continue**, **Start break**
- Break complete: **Continue**
- Exam countdown: **Open progress**, **Snooze**

Mutation actions operate on real repositories and the durable ATT01 ledger. Every action PendingIntent carries a self-contained occurrence payload so it remains valid after the original alarm bookkeeping is reconciled away.

### Deep links

Reminder launches carry the plan, type, target activity, and/or exact session ID. Review reminders open the review workspace; schedule reminders open the schedule workspace with the activity target; session reminders open the specific focus session when possible; summaries open Today; exam reminders open Progress.

### Reliability center

The Reminders screen reports:

- runtime notification permission and package state;
- channel readiness/blocking;
- battery-optimization state;
- exact-alarm capability while explicitly stating exact alarms are not required;
- next candidate time;
- last scheduler reconciliation;
- last delivered occurrence;
- last recovery trigger.

The screen links directly to Android notification, battery, and app settings and can force a reconciliation refresh.

### Debug Workbench

Reminder/Notification Lab now inspects all four channels and exposes ATT01 ledger totals plus ATT02 channel counts, exact-alarm capability, reconciliation/delivery timestamps, and the most recent recovery trigger.

## Persistence and compatibility

`StudyReminderOccurrenceStore` upgrades scheduled-occurrence persistence from the historical 4-field record to an 11-field record that preserves content, target, snooze ancestry, quiet-hour delay, and notification ID. The decoder remains compatible with the old 4-field format.

Delivered occurrence history remains bounded and ATT01 ledger migration remains intact.

## ATT01 regression hardening

ATT02 also repairs the generated Web SFLP runtime contract exposed by the ATT01 regression test: `StudyFlowAttentionLedgerSnapshotSchemaVersion` is now a runtime value rather than a type-only declaration, so the tracked JavaScript runtime and TypeScript source expose the same durable-ledger schema marker. This does not expand ATT02 into Web notification delivery; it prevents an Android overlay from carrying forward a stale ATT01 generated-runtime regression.

## Validation

Focused task:

```text
verifyAtt02AndroidAttentionSystem
```

It covers the ATT02 contract verifier plus shared domain, Reminders UI, Settings UI and Android app unit tests. An Android instrumentation test verifies real `SharedPreferences` migration/persistence for `AndroidStudyReminderPolicyStore`; connected instrumentation remains part of the later device/final release gate rather than the intermediate overlay apply.

## Scope boundary

ATT02 is Android-only delivery/product work on top of the shared ATT01 domain. Android widget-suite expansion is ATT03. Desktop, Web and TV delivery are ATT04–ATT06. Cross-device routing is ATT07.
