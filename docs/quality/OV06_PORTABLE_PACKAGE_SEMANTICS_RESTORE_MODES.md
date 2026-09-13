# OV06 — Portable package semantics & restore modes

Overlay 6 of 30 closes S3-01 through S3-08 from the final deduplicated audit.

## Contracts

- `FULL` portable packages include the complete current Study Plan graph, including parent-scoped language cards/review attempts and Termux request/result history.
- `REPLACE_EXISTING` clears the complete persisted target graph in reverse ownership order before inserting the projected import graph. Parent-scoped tables use relational ownership predicates instead of assuming a direct `studyPlanId` column.
- Point-in-time safety snapshots are a distinct `POINT_IN_TIME_SNAPSHOT` package purpose. They are schema-2, FULL, unredacted captures and may only restore over their original Study Plan identity. Exact restore does not remap IDs, statuses, timestamps, settings existence, or annex metadata.
- Portable annex metadata is imported only when matching annex bytes are present in the archive. Metadata-only annex rows are ignored rather than creating unusable annex records.
- `STRUCTURE_ONLY` and `TEMPLATE` always strip history/progress and device-local execution identity, including legislation read/important state, study-cycle cursor state, programming workspace identity, Termux roots/commands, language review attempts, and Termux execution history.
- `MERGE_COMPATIBLE` retains an existing target singleton parent only when one exists and rewrites imported child references to that canonical target singleton ID. Other parent/config rows are imported normally.
- Dry-run and real import use the same production projection/remapping path. The diff engine consumes projected rows instead of approximating IDs independently.
- The portable package schema is version 2. Writers emit only v2. Readers support v1..v2: legacy v1 may omit post-v1 tables, v2 requires the complete declared current inventory, and future schemas fail closed.

## Evidence

- `StudyPlanPortabilityOv06Test.fullReplaceCoversParentScopedLanguageAndTermuxRows`
- `StudyPlanPortabilityOv06Test.mergeRebindsChildrenToRetainedSingletonParent`
- `StudyPlanPortabilityOv06Test.exactSnapshotRestoresRootStateAndTimestamps`
- `StudyPlanPortabilityOv06Test.legacyV1MayOmitModernTablesButV2IsStrict`
- `StudyPlanOv06SemanticsTest.schemaAndPurposeContractsAreExplicit`
- `StudyPlanOv06SemanticsTest.structureAndTemplateStripProgressAndDeviceState`
- `StudyPlanOv06SemanticsTest.projectedDiffConsumesProductionRemappedIds`
- `scripts/verify_ov06_portable_package_semantics.py`
- `scripts/validate_ov06_portable_package_semantics.sh`

The validator compiles the Android instrumentation tests on every run. When an authorized ADB/Termux-ADB device is available, it additionally executes the connected Room/data instrumentation suites.
