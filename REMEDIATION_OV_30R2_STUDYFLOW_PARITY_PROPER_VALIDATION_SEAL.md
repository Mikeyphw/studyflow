# OV30R2 Studyflow Parity Proper Validation Seal

Overlay 30 of 30 corrective seal for `studyflow_parity`.

This seal replaces the earlier `--no-validate` delivery pattern with target-native validation during Devtool apply.

Required behavior:
- The overlay has its own Devtool target: `studyflow_parity`.
- The root Gradle task `verifyOv30R2StudyflowParityProperValidationSeal` exists and calls the target validation script.
- The Devtool target task graph references `verifyOv30R2StudyflowParityProperValidationSeal` or, for web, its npm validation equivalent.
- `.devtool-artifact/validation/validate.sh` executes this target seal during apply.
- The artifact is file-only and contains no unsafe archive paths.

Status: CLOSED / VALIDATED SEAL.
