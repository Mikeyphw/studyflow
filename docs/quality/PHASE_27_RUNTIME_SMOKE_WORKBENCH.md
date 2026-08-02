# Phase 27 Runtime Smoke Workbench

Status: implemented as a safe QA workbench from Settings.

Phase 27 adds manual runtime probes for the behaviors most likely to escape static validators:

- reminder reconciliation;
- Today widget refresh;
- backup dry-run path availability;
- global search query;
- recommendation query;
- local sync foundation inspection.

The workbench intentionally avoids destructive backup imports, cloud provider activation, network identity, and exact-alarm requests.


## r2 correction audit

- StudyFlowApp now imports `RuntimeSmokeWorkbench` and `RuntimeSmokeProbeKind` directly, so the runtime smoke route compiles instead of relying on missing symbols.
- The Settings route passes `syncState = syncState` into `SettingsScreen`, preserving the Phase 24 sync API contract.
- Probe execution is wrapped in `runCatching`; failures are surfaced as BLOCKED probe results instead of being silently counted as passes; probes are blocked on exceptions.
- Backup smoke now performs a real backup inspect and dry-run: it exports the default Study Plan to memory, calls `inspectBackup`, calls `dryRunImport`, and requires `databaseRowsChanged == 0` before passing.
- Default-plan-dependent probes are BLOCKED when no default Study Plan is configured.
- The release validation seal advances to Phase 27, while the Phase 26 verifier and tests are phase-forward compatible.
