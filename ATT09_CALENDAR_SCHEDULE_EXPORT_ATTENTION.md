# ATT09 — Calendar / Schedule Export Attention Integration

ATT09 adds a calendar export layer to the StudyFlow attention system without pretending that an external calendar is a StudyFlow delivery owner. The slice exports `.ics` events from planned schedule blocks, due reviews, mock exams and exam dates, and it creates ATT01-compatible attention occurrences that can still be routed by ATT07 and mirrored by ATT08.

## Product contract

- Calendar export is user-initiated. StudyFlow does not silently mutate Google Calendar, Outlook, Apple Calendar, Android Calendar Provider or desktop calendar databases.
- `.ics` is the canonical portable artifact. Provider-specific options are represented as targets, but ATT09 does not require account OAuth or a background sync account.
- Privacy is explicit: full details, private title, and busy-only exports are supported.
- Reminder lead times are represented both as ICS `VALARM` entries and as StudyFlow attention occurrences.
- Android TV and Web TV preview calendar attention but hand file export to phone, Web or desktop.
- ATT07 remains the owner router. Calendar export is a schedule projection and does not create a competing delivery owner.

## Validation

Focused gate: `verifyAtt09CalendarScheduleExportAttention`.

The gate checks the shared Kotlin export model, Android/Desktop/TV bridge contracts, Web ICS export runtime, React calendar export surface, runtime/dist parity, documentation, and regression gates from ATT01 through ATT08.
