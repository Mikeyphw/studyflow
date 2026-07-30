# StudyFlow Product Contract

## Mission

StudyFlow helps a person organize, execute, review, and adapt everything related to one or more study
objectives without requiring an account or a network connection.

## Root object

A **Study Plan** is not merely a schedule. It is the complete, self-contained workspace for one study
objective. It owns the target, syllabus, subjects, topics, schedule, sessions, goals, materials,
videos, annexes, questions, tests, mock exams, reviews, notes, error notebook, statistics, and plan
settings.

## Supported study types

- Public examination
- Certification
- University
- Entrance examination
- Free study

## Plan lifecycle

- `DRAFT`: incomplete configuration that is not yet driving study activity
- `ACTIVE`: currently used for recommendations, schedule, and notifications
- `PAUSED`: retained without active planning or notifications
- `COMPLETED`: objective finished and preserved for history
- `ARCHIVED`: hidden from normal active views but restorable

Deletion uses a recoverable trash stage before permanent deletion in the complete product.

## Product layers

1. **Organize**: define the objective, syllabus, availability, materials, and strategy.
2. **Execute**: study, track time, answer questions, review, and take mock exams.
3. **Adapt**: analyze performance and reorganize the plan with clear explanations.

## Offline-first behavior

All core plan operations work offline. Network access enhances media and future synchronization but
must not be required to open, edit, study, review, or export locally available content.

## Portability

A complete Study Plan can be created, edited, duplicated, paused, archived, deleted, imported, and
exported. The final package format is versioned, checksummed, and independent of the UI language.

## Non-goals for the foundation milestone

Phase 0 and Phase 1 do not implement Room persistence, account systems, cloud synchronization,
question-bank import, media playback, annex bytes, scheduling algorithms, or recommendation logic.
They establish contracts and replaceable seams for those capabilities.
