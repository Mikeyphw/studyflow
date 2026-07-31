# Phase 5: Syllabus, subjects and nested topics

Phase 5 implements Study Plan content organization.

## Added

- `:feature:subjects` Compose feature.
- Subject and nested topic screen with search, filters, sorting and bulk edit.
- Subject/topic editor dialogs.
- Structured-text and CSV syllabus import.
- `StudyContentRepository` and `ManageStudyContent` domain facade.
- `StudyProgressCalculator` for derived topic, subject and plan progress.
- Room-backed `RoomStudyContentRepository`.
- Room schema version 2 and migration `1 -> 2`.
- Optional exam-stage association for subjects and topics.
- Study Plan dashboard entry point for syllabus management.
- Phase 4 portability compatibility for older package rows without `examStageId`.

## Warning and lint cleanup

- Removes `LocalContext.current` resource lookups from portability UI.
- Adds a validator seal against `LocalContext.current.*getString` and `resources.getString`
  in Compose UI source.
- Keeps `LocalContext` only for platform services such as `ContentResolver`.

## Validation notes

This phase intentionally keeps the Room database hidden behind `:core:data`. The app module
receives only `StudyContentRepository` and does not depend directly on `:core:database`.
