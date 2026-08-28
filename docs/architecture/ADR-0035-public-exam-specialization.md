# ADR-0035 — Public-exam specialization reuses the Study Plan graph

## Status

Accepted by CPOV13.

## Decision

Public examinations are a specialization of the existing Study Plan graph, not a second product
model. Every persisted public-exam record carries plan ownership and all scheduling, sessions,
questions, reviews, mocks, goals and portability continue through the existing engines.

### Edital revisions

`ExamNoticeRevision` records provenance and a normalized-content identity. A revision preview is
bound to both the latest accepted revision and a canonical syllabus hash. Apply is explicit and
version-bound. Removed edital content is archived by default rather than silently deleted.

### Study cycle

`StudyCycle` and `StudyCycleItem` persist only ordering, eligibility and the current pointer. They do
not duplicate Study Sessions or the schedule database. A Study Session stores a cycle binding in the
existing metadata channel; completion advances the pointer transactionally and cancellation does
not.

### Explainable priority

The priority engine is pure and deterministic. It ranks existing eligible content from explicit
signals such as exam weight, incidence, mastery, accuracy, errors, reviews, adherence, exam distance,
cycle position and recent saturation. Recommendations expose reason codes and never depend on an
opaque model call.

### Banca blueprints

Mock blueprints select existing questions and freeze their section/scoring policy into the existing
mock configuration payload when the attempt begins. Scoring therefore remains reproducible after UI
changes or process restart. No second mock-exam store exists.

### Legislation

Legislation uses structured document/provision records because article-level state and evidence
links need durable identity. Imports are local and preview-first. Question/error/review links carry
plan ownership and are included in backup/restore. The feature does not claim an automatic online
legal feed.

## Consequences

- Room schema 13 is the final CPOV schema endpoint.
- Dashboard and final-stretch outputs stay derived; they are not persisted as authoritative facts.
- Backup/restore and plan duplication must cover every persisted CPOV concept.
- Unknown exam data remains unknown rather than being fabricated.
