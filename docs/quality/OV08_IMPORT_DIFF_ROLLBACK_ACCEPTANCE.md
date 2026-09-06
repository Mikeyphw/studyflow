# OV08 Import Diff and Rollback Preview Acceptance

OV08 upgrades `.studyplan` dry-run from a row-count check to a target-aware change preview.

- Create-as-new reports all incoming rows as additions.
- Replace compares stable row identities and reports additions, removals, changed rows, and unchanged rows.
- Compatible merge mirrors the existing merge contract: protected structural tables are explicitly reported as ignored, while eligible imported rows are additions and existing target rows remain untouched.
- The merge skip list is centralized in `StudyPlanImportCompatibility` so execution and preview cannot drift.
- Dry-run remains zero-write (`databaseRowsChanged = 0`).
- Real imports remain Room-transaction protected. Replace/merge continue creating durable pre-import safety snapshots before database commit.
- The Plans UI presents the aggregate diff and per-table non-zero changes before the user executes an import.
