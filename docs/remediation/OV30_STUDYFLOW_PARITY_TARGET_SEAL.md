# OV30 studyflow_parity — Parity aggregate terminal target seal

Overlay 30H of the terminal **Overlay 30 of 30** remediation split seals the `studyflow_parity` Devtool target independently.

## Delivered

- Adds a target-scoped executable verifier: `scripts/verify_ov30_studyflow_parity_target_seal.py`.
- Adds a target-scoped validator entry point: `scripts/validate_ov30_studyflow_parity_target_seal.sh`.
- Wires the target to its own terminal gate instead of relying on a single mixed-target overlay.
- Preserves the REL-FINAL boundary: source/static target seals do not claim signed artifact, device, operator, release-ready, or release-ops-ready evidence.

## Gradle/runner gate

- Target: `studyflow_parity`
- Gate: `verifyOv30StudyflowParityTargetSeal`

## Verification

```bash
bash scripts/validate_ov30_studyflow_parity_target_seal.sh
```
