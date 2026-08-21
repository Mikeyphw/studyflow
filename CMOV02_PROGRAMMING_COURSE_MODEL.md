# CMOV-02 — Programming Course Model

This overlay adds the first persistent programming-course model on top of CMOV-01.

## Delivered

- Programming course profile model.
- Programming language and course-level enums.
- Code snippet model.
- Coding exercise model with prompt, starter code, expected output, hints, solution notes,
  difficulty, status, files, retry date, and safe run/test command templates.
- Mini project model with checkpoints and deliverables.
- Termux runtime preference model that is stored but still disabled by default.
- Room entities, DAO, schema version 6, and migration 5 to 6.
- Plan duplication/export/import table inventory for programming-course rows.
- Contract verifier: `verifyProgrammingCourseModel`.

## Deferred

No code execution is enabled here. Termux runtime stays metadata-only until the runtime foundation overlay.
