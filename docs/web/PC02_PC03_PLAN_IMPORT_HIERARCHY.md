# WEB-PC02/PC03 — Plan import and hierarchy parity closure

## PC02 — Generic Plan Content import

Plan Content now accepts local TXT, Markdown, CSV, TSV, and JSON text sources. The import path is deliberately staged:

`parse → normalize → validate → preview/diff → explicit apply`

Preview generation never mutates live state and is revision-bound. Unsupported/invalid rows and ambiguous existing-name matches block Apply. Duplicate input paths are reported and skipped rather than multiplied silently. Merge mode only adds/updates/reactivates represented content. Reconcile mode additionally archives active subjects/topics omitted from the imported source; it never deletes historical content.

Imports can target the active plan or explicitly create a new plan. New-plan import creates canonical StudyFlow entities only when Apply is pressed and then activates the new plan. TXT supports `Subject:` / `Topic:` lines and `Subject > Topic > Child` paths. Markdown uses level-1 subjects and deeper headings as topic hierarchy. CSV/TSV support subject/topic/path/parent plus optional weight, priority, status, and notes fields. JSON supports flat rows or nested `subjects[].topics[].children`.

## PC03 — Plan lifecycle and nested topics

StudyFlow now supports multiple local plan workspaces with create, rename, switch, structure duplicate, archive/restore, and guarded delete. Archived plans retain all content and history. Delete is intentionally conservative: the plan must be archived, inactive, and own no remaining records. Duplicate copies plan configuration, subjects, nested topics, the question bank, goals, and currently planned activities; it deliberately does not clone sessions, evidence, reviews, errors, or attachment-backed materials as if they were new history.

Topics have a nullable `parentTopicId`. Parents must belong to the same plan and subject. Self-parenting and ancestry cycles are rejected, deletion is blocked while child topics exist, and reorder is scoped to siblings under the same parent.

State schema **v6** adds nested-topic parent links and `studyCycles`, a per-plan map of study-cycle state. The existing top-level `studyCycle` remains the active-plan compatibility view. Switching plans restores that plan's cursor instead of overwriting another plan's cycle. Existing v5/v4/v3/v2/v1 state migrates forward without losing canonical records.

## Boundaries

- No network/backend/account dependency is introduced.
- Generic import is local text parsing only; PDF extraction is outside this closure.
- Preview before mutation is mandatory.
- Import reconciliation archives; it does not silently delete.
- Plan duplication does not fabricate study evidence/history.
- `.devtool.toml` is untouched.
