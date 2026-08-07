# Phase 60-61: Smart Content Cleanup and Exam Report Export

## Phase 60: Smart Content Cleanup

StudyFlow now derives a local content-health report from the active plan, syllabus, question bank,
library, review queue, schedule, flashcards, error notebook, and study notes.

The domain engine detects:

- normalized duplicate questions while retaining the oldest candidate as the canonical preview item;
- active orphan topics with no child topics or linked study activity;
- old detached archived materials and completed videos;
- active or paused plans with no recent content/completion evidence;
- case/spacing variants of tags;
- pending reviews whose linked subject/topic/question/flashcard/error entry no longer exists; and
- planned/in-progress schedule rows that point at inactive or missing content.

The result is structured as issue kinds, severities, counts, affected IDs, a health score, and preview
actions. The engine has no repository mutation dependency. Every generated action is non-destructive
and requires confirmation. The V2 Plan Content workspace shows the health summary, a preview-only
safety notice, and localized issue explanations. Progress also surfaces the health summary and routes
back to the Content workspace for review.

## Phase 61: Exportable Exam Report

The V2 Progress workspace builds a structured exam-report snapshot from existing offline evidence:

- plan identity and generation time;
- readiness score, band, and signal scores;
- weekly coach items;
- seven-day study/evidence totals;
- weak areas;
- missed work and due/overdue reviews;
- practice-test counts and aggregate submitted-test accuracy;
- mock-exam counts and best/average scores;
- recent question additions;
- executable adaptive-reschedule count;
- Phase 60 content-health totals; and
- structured next-safe-action kinds.

The workspace maps those domain values to English or pt-BR strings before export. Freeform study
notes are not part of the report model. Export is explicit and local: the user chooses HTML or PDF,
StudyFlow writes the file under its `exam-reports/` cache directory, obtains a FileProvider URI, and
opens Android's share/save chooser. No network client, cloud account, or background upload is used.

## Warning cleanup

The Phase 58-59 warning-suppression boundary is retired for the primary app/settings/workspace
resource files. Thirty visible count strings now use Android plurals and Compose
`pluralStringResource`. Truly unused compatibility resources were removed, while live resources such
as `complete` were retained without suppression. The Phase 58-59 verifier was strengthened to reject
remaining targeted suppressions in these primary resource files.

## Verification

`verifyPhase60_61ContentReportSeal` executes `scripts/verify_phase_60_61.py`. The verifier checks
behavioral cleanup contracts, preview-only safety, structured report boundaries, local FileProvider
export, bilingual resource parity, plural migration, removed unused resources, resource-reference
integrity, tests, source formatting, Navigation 2.9.8, and unchanged toolchain versions.

Android compilation, unit tests, resource processing, APK assembly, and lint remain target-machine
Devtool gates.
