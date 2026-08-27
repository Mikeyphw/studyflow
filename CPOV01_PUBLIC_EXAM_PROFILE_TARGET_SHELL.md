# CPOV01 — Public Examination Profile & Target Shell

CPOV01 turns the existing one-per-plan `StudyTarget` and its `ExamStage` rows into a first-class public-exam profile.

Delivered scope:

- Plan Settings shows the profile only for `StudyType.PUBLIC_EXAMINATION`.
- Existing organization, role/cargo, exam board, edital reference, confirmed/expected dates, openings and estimated cutoff are editable.
- Existing exam stages are editable and new stages can be added without a second persistence model.
- `StudyTarget` is observable as a Room `Flow`; stages continue to come from the repository-backed syllabus flow, so saved values restore after recreation.
- Target and stage IDs are guarded against cross-plan re-parenting before Room upserts.
- The public-exam onboarding sample never invents organization, board or dates and routes directly to Plan Settings after creation.
- English and Brazilian Portuguese resource coverage is kept in parity.
- No database schema version bump and no new canonical table/entity were introduced.

CPOV01 intentionally does not import edital content or keep notice revision history; those belong to CPOV02/CPOV03.
