# Desktop DOV18 — Concurso Público parity

DOV18 moves Concurso Público from a missing Desktop capability to a persistent shared-domain workflow.

## Delivered

- Public-exam target editing reuses the canonical `StudyTarget` attached to the selected Study Plan.
- Edital parsing, revision preview/diff and coverage reuse `ExamNoticeImportParser` and `ExamNoticeRevisionPlanner`.
- Accepted revisions mutate the canonical Desktop syllabus while preserving matching subject/topic IDs and archiving removals.
- Legislation documents/provisions and evidence-link ownership are durable in `public-exam-v1.properties`.
- Persistent study-cycle ordering, enable/skip/move semantics reuse `StudyCyclePlanner`.
- Study-cycle session bindings survive restart; completion advances the pointer and cancellation does not.
- The Desktop Concurso workspace derives the same `PublicExamDashboardBuilder` and final-stretch allocation as other clients.
- Banca/organization/role/revision provenance continues to use the shared question model delivered by the public-exam track.

## Boundary

This milestone does not create a second syllabus, practice, review or session database. Native document pickers remain DOV23.
Portable public-exam export/import is part of DOV20.
