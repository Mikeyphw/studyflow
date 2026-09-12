# Calendar attention export

StudyFlow calendar export is designed for interoperability and truthfulness. Exporting a calendar file produces standard ICS events and optional `VALARM` reminders. The same schedule rows can also produce StudyFlow attention occurrences, which are delivered by the ATT07 cross-device router instead of by the calendar module itself.

## Supported surfaces

| Surface | Export behavior | Background claim |
| --- | --- | --- |
| Android phone/tablet | User-initiated ICS payload/share target | Android reminders remain owned by StudyFlow alarm/notification runtime |
| Desktop | User-initiated ICS file export | Desktop tray/runtime remains the StudyFlow owner when available |
| Web/PWA | Download/copy ICS from Progress → Tools | Browser/PWA reminders remain ATT05/ATT07 governed |
| Android TV / Web TV | Preview only; continue on phone/desktop/Web | Foreground-only TV truth from ATT06 is preserved |

## Privacy modes

- **Full details** exports the activity title, subject/body and StudyFlow route.
- **Private title** exports a generic StudyFlow block title and keeps the details inside StudyFlow.
- **Busy only** exports busy time with no study detail.

## Non-goals

ATT09 does not add OAuth calendar write access, hidden provider accounts, or closed-device reminder guarantees. Those can be added later as explicit, user-authorized provider connectors without weakening ATT07 ownership truth.
