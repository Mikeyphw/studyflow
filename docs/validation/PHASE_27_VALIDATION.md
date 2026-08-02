# Phase 27 Validation

Source checks:

- `scripts/verify_phase_27.py`
- `RuntimeSmokeWorkbenchTest`
- Settings route/button presence
- localized English and Portuguese smoke strings

Device smoke still required:

- open Settings;
- open Runtime smoke workbench;
- run the safe probe set with a default Study Plan;
- verify reminder reconciliation and widget refresh do not crash;
- verify backup drill remains dry-run-oriented.


## Phase 27 r2 checks

- Verifies runtime smoke imports and Settings `syncState` wiring.
- Verifies release seal phase-forward behavior after Phase 27.
- Verifies backup smoke uses `exportPlan`, `inspectBackup`, and `dryRunImport` with a zero-write assertion.
- Verifies probes are blocked on exceptions rather than reported as successful.
