# OV30R2 — Studyflow Tv Release proper validation task and seal

## Scope

Target: `studyflow_tv_release`  
Roadmap position: Overlay 30 of 30, corrective R2 split seal `30F`.

## Contract

The first OV30 split was structurally correct, but it was applied with `--no-validate`. This R2 overlay makes the target seal self-validating:

1. Adds `verifyOv30R2StudyflowTvReleaseProperValidationSeal` as a first-class Gradle validation task.
2. Wires the matching Devtool target task/phase graph to that validation task or native web validation script.
3. Updates the artifact validation hook so Devtool validation runs the target seal during apply.
4. Leaves product code unchanged except validation/seal wiring and evidence docs.

## Evidence

- Target validator: `scripts/verify_ov30r2_studyflow_tv_release_proper_validation_seal.py`
- Shell entrypoint: `scripts/validate_ov30r2_studyflow_tv_release_proper_validation_seal.sh`
- Devtool hook: `.devtool-artifact/validation/validate.sh`

Result expected from Devtool validation:

```text
OV30R2_STUDYFLOW_TV_RELEASE_PROPER_VALIDATION_SEAL: PASS
```
