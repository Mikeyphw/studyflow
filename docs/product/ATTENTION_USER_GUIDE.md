# StudyFlow Attention — user guide

StudyFlow Attention covers reminders, notification state, quiet hours, cross-device routing, compact surfaces, calendar export, and reliability evidence. The ATT01 ledger is the shared history behind these surfaces.

## Android

Android is the strongest native reminder client. In **Reminders / Reliability Center** you can enable categories, configure timing, use quiet hours and focus profiles, inspect scheduled/recent reminder state, and review ATT11 reliability evidence. Android notifications support the reminder actions exposed by the occurrence. Home-screen widgets are independent glance surfaces and do not require notification permission for freshness.

When ATT07 selects another explicit target device, Android keeps its replicated occurrence in the ledger but does not surface it locally. This prevents explicit-target reminders from becoming accidental broadcasts.

## Desktop

Desktop reconciles reminders while the StudyFlow Desktop process is running. Native notifications and the tray mini-dashboard are used when supported. If the platform does not provide those capabilities, StudyFlow keeps reminder state truthful rather than claiming background delivery. Desktop exposes focus/quiet-hours controls plus ATT11 reliability diagnostics and JSON/CSV audit copy actions.

Desktop is not an operating-system reminder daemon and ATT12 does not add that claim.

## Normal Web / PWA

Open **Notifications** to use the in-app Notification Center, category/timing controls, ATT10 focus policy, compact projections, ATT11 analytics, and JSON/CSV audit export. Browser notification permission is optional.

Without an actual Web Push subscription and relay, closed-browser delivery is **foreground-only/best effort**. Browser API availability alone is not counted as successful background reliability evidence.

## Android TV and Web TV

TV clients show Attention only while the TV experience is open. Banners are D-pad/remote safe and expose up to three bounded actions appropriate to the reminder, including Open/Start/Review/Continue, Snooze, Dismiss, or Move to phone. TV does not pretend to own closed-app alarms or system notifications.

Android TV and Web TV show read-only focus/reliability projections. High-input policy editing and audit export continue on a phone, Desktop, or normal Web client.

## Quiet hours and focus profiles

ATT10 supports Balanced, Exam, Work, Sleep, Travel/offline, and Minimal/TV profiles. Quiet windows are timezone-aware and support overnight ranges. Category policy can deliver normally, defer, stay silent, use compact-only presentation, route to phone, request bounded mirror escalation, or require explicit action. Deferred reminders remain in the ATT01 ledger.

## Cross-device ownership

ATT07 chooses one owner and an optional bounded mirror set from actual device capabilities. An explicit `targetDeviceIds` list is authoritative. ATT12 seals this at the final delivery call sites on Android, Desktop, and Web so merely possessing a replicated ledger entry is not permission to notify.

## Calendar export

Progress → Tools → **Calendar export** on normal Web, plus the Android/Desktop bridges, can generate portable `.ics` data with `VEVENT` and `VALARM`. Full/private/busy-only privacy modes are available. TV previews the export and hands file creation to another client. StudyFlow does not silently write an external calendar provider.

## Reliability and audit evidence

ATT11 derives its evidence from the existing ATT01 ledger plus ATT10 policy audit and ATT07 route audit metadata. It counts delivered, missed, deferred, snoozed, dismissed, and done outcomes and explains owner/mirror routing. JSON/CSV export is user initiated and contains diagnostic evidence rather than a second reminder database.
