# Phase 8: Question Bank and Practice Execution

Phase 8 adds the first practice layer on top of the existing Phase 2 practice tables. It keeps the
Room schema at version 2 and exposes questions, practice tests and mock exams through the domain
and data layers without leaking database implementation types into the app module.

## Scope

- `:feature:practice` Compose module for question-bank and practice-test entry points.
- Domain-facing `StudyPracticeRepository` and `ManageStudyPractice` use case.
- `PracticeAnalytics` summary and deterministic deck selection.
- Room-backed `RoomStudyPracticeRepository`.
- Practice DAO operations for question lookup, deletion, tests and mock exams.
- Entity mappers for questions, practice tests and mock exams.
- Dashboard navigation to practice.
- Bilingual English/PT-BR strings.

## Non-goals

- No database migration. Existing `questions`, `study_tests` and `mock_exams` tables are reused.
- No full per-question answer ledger yet. Test and mock aggregates are recorded first.
- Bulk PDF/OCR imports remain outside this phase.
