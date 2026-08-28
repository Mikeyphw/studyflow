# CPOV03 + CPOV04 — Edital revisions and persistent study cycle

This combined overlay advances the public-examination specialization from normalized edital preview into auditable revision history and a persistent, session-backed study cycle.

## CPOV03 — Edital revision history, diff and coverage

- Adds `ExamNoticeRevision` provenance without duplicating the canonical syllabus tree.
- Room 9→10 adds `exam_notice_revisions` with plan/target ownership and content-hash indexes.
- Every Apply is bound to both the latest accepted revision and the canonical syllabus SHA captured at preview time.
- Diff output is deterministic: added, removed, changed/renamed and unchanged.
- Removed subjects/topics are archived by default; no destructive delete is performed by revision apply.
- Coverage is derived from the current canonical active syllabus.
- Android Content shows revision impact before Apply plus latest revision and coverage.
- Full portability includes revision metadata; history-redacted/private exports exclude it.

## CPOV04 — Persistent study cycle

- Room 10→11 adds `study_cycles` and `study_cycle_items`.
- Cycle items reference existing subjects/topics and only own ordering, enabled state, target duration and pointer state.
- Archived/deleted content is automatically ineligible through local joins.
- Supports build/rebuild, enable/disable, skip once and move-current-to-end.
- Today quick-start delegates to the existing `ManageStudySession` API.
- Session creation persists a cycle binding in existing database metadata in the same transaction.
- Completion advances the bound cycle pointer in the same Room transaction as terminal Study Session completion.
- Cancellation clears the binding without advancing the pointer.
- Active sessions therefore survive process restart without creating a second session/schedule database.
- Missed calendar days never mutate the cycle pointer by themselves.

## Persistence and portability

The migration chain is intentionally split:

1. 9→10: revision history only.
2. 10→11: cycle state/items only.

Exported Room schemas `10.json` and `11.json`, a 9→11 migration fixture, graph duplication specs, relational ownership validation and privacy-history classification are included.

## Validation

`verifyCpov03Cpov04PublicExamProgress` checks schema, migrations, preview binding, archive semantics, atomic session/cycle binding, portability, Android surfaces, bilingual resources and focused regression tests. Full Android/Room compilation remains authoritative in Devtool.
