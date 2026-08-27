# CPOV02 — Edital Import Preview & Normalization

CPOV02 adds a local, preview-first edital import path on top of the existing syllabus model and repository mutation path.

Delivered scope:

- `ExamNoticeImportParser` normalizes edital input into the existing `SyllabusImportDraft` contract.
- Structured text supports the existing indentation hierarchy plus common `Disciplina:`/`Matéria:` headings and numbered
  topic paths such as `1`, `1.1`, and `1.1.1`.
- CSV remains supported and TSV is added through the same strict delimited parser.
- Android can open local TXT, CSV, TSV, and PDF documents without uploading them.
- Text-bearing PDF extraction uses the Android platform PDF text layer on Android 15+.
- PDFs without a readable text layer surface a truthful no-text-layer result. OCR for scanned-image PDFs is out of scope.
- Preview shows the normalized subject/topic tree, duplicates, unrecognized headings, orphan topics, and the destructive
  change count before Apply is enabled.
- CPOV02 imports are additive: existing syllabus rows are not deleted or overwritten, so destructive changes remain zero.
- Editing the source invalidates the preview; Apply only receives a draft from the latest successful preview.
- Preview analysis validates `StudyPlanId` ownership before comparing against the existing syllabus.
- English and Brazilian Portuguese resources are kept in parity.

Persistence remains unchanged. CPOV02 does not add a Room entity, schema migration, edital revision table, or automatic
online edital fetch. Revision history and edital diffs belong to CPOV03.

## CPOV01 carry-forward compile hotfix

- Removes the explicit `androidx.compose.foundation.layout.weight` import from `PublicExamProfileSection.kt`.
- `Modifier.weight(...)` remains unchanged inside `Row {}` and resolves through the public `RowScope` member extension.
- This avoids accidentally resolving Compose's internal `RowColumnParentData.weight` symbol with the current Compose toolchain.
