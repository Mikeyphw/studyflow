# WTV12–WTV13 — TV Progress analytics and read-only Schedule

## WTV12 — dedicated TV Progress & analytics

The browser-TV adapter now owns a dedicated 10-foot Progress surface instead of reusing the full desktop/mobile Progress tabs.

Delivered behavior:

- weekly completed study minutes from canonical completed sessions;
- seven-day question volume and graded accuracy from persisted `question-attempt` evidence;
- readiness and topic coverage from the existing truthful StudyFlow progress/readiness models;
- per-subject coverage, recent question accuracy, due reviews, open errors and completed study minutes;
- deterministic weak-subject ranking from those persisted signals only;
- recommended TV destinations for weak areas without creating or mutating evidence;
- D-pad/phone actions for Today, Question Set, Reviews, Mock Exam and Schedule;
- explicit `No data` presentation when accuracy/readiness/coverage has no evidence.

The TV Progress surface is intentionally navigation/read-only. Goal creation/completion, cleanup application, backup/import, destructive reset and report export remain on phone/computer.

## WTV13 — dedicated read-only TV Schedule

`SCHEDULE` is now a real TV History API surface at `#schedule` rather than only the Plan/Schedule tab.

Delivered behavior:

- Today and This week modes backed by `deriveScheduleCalendarSnapshot`;
- visible scheduled minutes plus planned/completed/cancelled counts;
- deterministic date/time ordering and Today/Tomorrow labels;
- selected-item presentation with Previous/Next remote navigation;
- launch routing to Study Session, Question Set, Reviews or Mock Exam using existing canonical activity routing;
- writing activities remain visible but explicitly continue on phone/computer;
- Plan/Schedule includes a direct **Open TV Schedule** action;
- the TV navigation rail includes Schedule;
- paired-phone shortcuts for Today/week schedule modes and Progress.

The Schedule surface has no schedule-authoring or mutation API: no configuration changes, preview/apply, activity locks, bulk changes, rescheduling, imports, deletion or file operations.
