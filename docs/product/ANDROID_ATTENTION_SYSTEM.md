# Android Attention System

Android is StudyFlow's reference background reminder client.

## Capabilities

| Capability | Android ATT02 |
| --- | --- |
| Planned study reminders | Full |
| Due-review reminders | Full |
| Missed-activity recovery alerts | Full |
| Configurable daily summary | Full |
| Session ending/completion timers | Full |
| Optional exam countdown | Full |
| Quiet hours | Full |
| Snooze | Full |
| Start/mark-done actions | Full |
| Exact-target navigation | Full |
| Reboot/time/time-zone/package recovery | Full |
| Live schedule/review/session invalidation | Full |
| Four OS notification channels | Full |
| Exact-alarm permission required | No |
| Reliability diagnostics | Full |
| ATT01 durable ledger integration | Full |

## Design rule

A reminder is shared StudyFlow domain state. `AlarmManager` and Android notifications are delivery mechanisms only. Android uses inexact `setWindow` alarms by default and never presents exact-alarm permission as a normal requirement.

## User policy

Each plan has an Android reminder policy with independent category switches and timing values. Existing installs are migrated from the legacy review-notification switch on first use. Daily summaries default to 08:00 local time.

## Recovery

StudyFlow reconciles alarms after system clock/environment changes, package updates, app resume and relevant repository changes. Disabling notification permission stops Android delivery but does not falsely mark the semantic occurrence permanently cancelled.

## Next boundary

ATT03 expands Android's glanceable surface into the full widget suite; it does not redefine ATT02 notification scheduling.
