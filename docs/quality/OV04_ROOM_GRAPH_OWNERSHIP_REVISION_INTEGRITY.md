# OV04 — Room graph ownership & revision integrity

Overlay **4 of 30** closes the database-integrity findings S2-01, S2-02, S2-03, S2-04, S2-08, S2-09, S2-10 and S2-12.

## Runtime changes

- Study Sessions now have one database-enforced active session globally, matching the repository/domain contract.
- Graph revision triggers cover the complete modern user-data graph, including exam revisions, study cycles, programming/Termux rows and language rows.
- Modern plan-owned tables receive stable-owner/nonblank guards and scoped-reference guards.
- Question exam stage/edit-al revision provenance is enforced at SQLite level.
- Study-cycle `currentItemId` is an owned durable pointer; dangling/foreign pointers are rejected and deleting the current item clears the pointer.
- `moveTopic` rebinds `code_snippets`, `coding_exercises`, `mini_projects` and `study_cycle_items` together with the legacy linked rows.
- Database diagnostics reuse `StudyFlowIntegrity.logicalIntegrityViolationCount`, so modern corruption cannot report healthy.
- Schema 14 adds an explicit 13→14 trigger-replacement migration. Managed triggers are dropped/recreated; semantic changes no longer depend on `CREATE TRIGGER IF NOT EXISTS`.

## Evidence

`StudyFlowOv04IntegrityTest` exercises global active-session enforcement, all modern revision-trigger registrations, cross-plan rejection, question provenance, cycle-pointer safety and modern-graph diagnostics. `StudyTopicMoveModernOwnershipTest` executes the real Room content repository move operation. `StudyFlowMigration13To14IntegrityTest` proves a stale legacy trigger is replaced by schema 14 migration.

Run `./scripts/validate_ov04_room_graph_integrity.sh` after applying the intermediate overlay.
