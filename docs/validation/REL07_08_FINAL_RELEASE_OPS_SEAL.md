# REL07/REL08 final release-ops seal

This overlay finishes the post-SFLP release-operation track after REL05/REL06.

## REL07 release checklist gate

Marker: `REL07_RELEASE_CHECKLIST_GATE`.

REL07 adds `scripts/check_release_operator_checklist.py`. It reads the REL01 through REL06 evidence directory and writes:

- `release-checklist-gate.json`
- `release-checklist-gate.md`
- `release-checklist-command-template.sh`

The gate is fail-closed. Missing target evidence, blocked evidence, or unchecked operator review items produce `PENDING_TARGET_EXECUTION` or `BLOCKED`. It never builds, signs, installs, uploads, publishes, deletes, or rolls back artifacts.

## REL08 final release-ops seal

Marker: `REL08_FINAL_RELEASE_OPS_SEAL`.

REL08 adds `scripts/build_release_ops_final_seal.py`. It consumes REL01 through REL07 evidence and writes:

- `release-ops-final-seal.json`
- `release-ops-final-seal.md`
- `studyflow-release-ops-final-bundle.zip`

`releaseOpsReady` is true only when every REL01 through REL07 input is `PASS` and REL07 has `readyForFinalReleaseOpsSeal=true`. Operator publishing remains outside this repository automation.

## Validation

`verifyRel07Rel08FinalReleaseOpsSeal` depends on `verifyRel05Rel06ReleaseNotesRollback` and then runs `scripts/verify_rel07_08_final_release_ops_seal.py`.
