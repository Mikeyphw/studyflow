# StudyFlow Product Contract

## Mission

StudyFlow helps a person organize, execute, review, and adapt everything related to one or more study
objectives without requiring an account or a network connection.

## Root object

A **Study Plan** is not merely a schedule. It is the complete, self-contained workspace for one study
objective. It owns the target, syllabus, subjects, topics, schedule, sessions, goals, materials,
videos, annexes, questions, tests, mock exams, reviews, notes, error notebook, statistics, local
checkpoint evidence, and plan settings.

## Supported study types

- Public examination
- Certification
- University
- Entrance examination
- Free study

## Plan lifecycle

- `DRAFT`: incomplete configuration that is not yet driving study activity
- `ACTIVE`: currently used for recommendations, schedule, reminders, and widgets
- `PAUSED`: retained without active planning or reminders
- `COMPLETED`: objective finished and preserved for history
- `ARCHIVED`: hidden from normal active views but restorable

Deletion uses a recoverable trash stage before permanent deletion.

## Product layers

1. **Organize**: define the objective, syllabus, availability, materials, and strategy.
2. **Execute**: study, track time, answer questions, review, and take mock exams.
3. **Adapt**: analyze performance and reorganize the plan with clear explanations.

## Offline-first behavior

All core plan operations work offline. Local data is authoritative. Network availability may affect
direct web media, but it is not required to open, edit, study, review, export, inspect, or dry-run
locally available content.

## Portability

A complete Study Plan can be created, edited, duplicated, paused, archived, deleted, imported, and
exported. The package format is versioned, checksummed, privacy-audited, and independent of the UI
language.

## Current product boundaries

StudyFlow does not present an account or online-provider setup. Local checkpoint status summarizes
on-device backup/readiness conditions. Release packaging and signed artifact policy are separate
release-phase work.
