# ADR-0010: Study Plan syllabus content structure

## Status

Accepted for Phase 5.

## Context

A Study Plan needs an editable content tree that can represent concurso edital sections,
manual study subjects, nested topics, and later exam-stage targeting. This structure must
remain local, portable, and isolated by `studyPlanId`.

## Decision

Subjects and topics remain first-class entities owned by a `StudyPlan`. Topics support a
nullable parent topic to model nested syllabus trees. Subject and topic progress is derived
from active topic data instead of being edited as a disconnected percentage.

Phase 5 introduces a `StudyContentRepository` and `ManageStudyContent` domain facade for
syllabus operations. The Room implementation is hidden inside `:core:data`; UI modules use
domain contracts only.

Schema version 2 adds optional `examStageId` columns to subjects and topics. These links are
nullable so old Phase 4 packages and databases import or migrate safely.

## Consequences

- Deleting a subject deletes its topics through Room cascades.
- Deleting a topic makes direct child topics roots via `SET_NULL`.
- Reordering stores explicit `position` values.
- Importing a syllabus appends new subjects and topics without replacing existing content.
- Export/import normalizes older package rows that do not yet include `examStageId`.
