# CPOV13 — Public Exam Full Integration & Release Seal

CPOV13 closes the concurso-publico specialization roadmap without adding another product model or
Room schema version. Schema 13 remains the canonical endpoint. The seal reconciles documentation,
migrations, portability, connected acceptance and target-machine validation into one fail-closed
release contract.

## Database endpoint

The pre-CPOV schema is 9. The required public-exam migration path is:

`9 -> 10 -> 11 -> 12 -> 13`

- 9 -> 10: edital revision provenance.
- 10 -> 11: persistent study cycles.
- 11 -> 12: question provenance and banca filters.
- 12 -> 13: structured legislation documents, provisions and evidence links.

Fresh schema creation and the full incremental path are both covered by Android migration tests.
The exported Room schemas 9, 10, 11, 12 and 13 remain source-controlled.

## Portability endpoint

Full Study Plan packages include and ownership-remap all CPOV persistence:

- `study_targets` and `exam_stages`;
- `exam_notice_revisions`;
- `study_cycles` and `study_cycle_items`;
- extended `questions` provenance;
- `legislation_documents`, `legislation_provisions` and `legislation_evidence_links`.

CPOV13 adds a connected portability seal that exports a representative public-exam graph, restores
it as a new Study Plan and verifies that remapped foreign keys no longer point to the source plan.

## Connected product journey

When a device is present, `:app:connectedReleaseAndroidTest` includes the CPOV journey. It creates a
public-exam plan, configures target/banca/date, previews/applies an edital revision, creates and
executes a study cycle, records questions/errors/reviews, executes a frozen banca blueprint mock,
derives the concurso dashboard, applies a final-stretch sprint preview, and performs a real
backup/create-as-new restore. The existing authoritative full-journey acceptance test remains the
process-kill/restart persistence gate.

No root access is required. Root probing belongs only to optional diagnostics and is never an
acceptance prerequisite.

## Target-machine gates

The final Devtool validation preserves the existing fail-closed separation:

1. source/toolchain/CPOV/repository contracts;
2. all Android and JVM unit tests;
3. Debug + Release compilation and resource processing;
4. Debug + Release lint across Android modules;
5. Debug + Release APK assembly and Release AAB bundling;
6. desktop compile/test regression;
7. connected migration/workspace/app journeys when a device is present.

The repository's signed-artifact and manual-device evidence framework remains authoritative for a
product-sealed release verdict. A green source/build validation does not manufacture signed or
manual device evidence.

Source/build validation does not manufacture signed or manual device evidence.

source/build validation does not manufacture signed or manual device evidence.
