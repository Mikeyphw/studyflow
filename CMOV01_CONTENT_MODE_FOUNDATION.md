# CMOV-01 Content Mode Foundation

This overlay adds the first content-mode layer for StudyFlow without changing the
existing exam-plan behavior. Existing plans default to `EXAM_PLAN`; new and edited
plans can explicitly select a mode.

## Modes

- `EXAM_PLAN`
- `PROGRAMMING_COURSE`
- `LANGUAGE_DECK`
- `READING_STUDY`
- `GENERAL_SKILL`

## Programming readiness

`PROGRAMMING_COURSE` advertises code-block support as a planned capability while
leaving Termux runtime and coding exercises disabled until later overlays. This
keeps CMOV-01 as schema/UI foundation only.

## Persistence and portability

The `study_plans` table now stores `contentMode`. Migration 4→5 backfills all
existing rows as `EXAM_PLAN`. Study Plan packages include the new root column and
legacy packages without `contentMode` import as `EXAM_PLAN`.

## UI

The plan editor now asks “What are you studying?” and shows the five content
modes. The plan list shows a content-mode pill on each plan card.
