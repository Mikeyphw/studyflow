# OV30 studyflow_release — Android release evidence target seal

Overlay 30E of the terminal **Overlay 30 of 30** remediation split seals the `studyflow_release` Devtool target independently.

## Delivered

- Adds a target-scoped executable verifier: `scripts/verify_ov30_studyflow_release_target_seal.py`.
- Adds a target-scoped validator entry point: `scripts/validate_ov30_studyflow_release_target_seal.sh`.
- Wires the target to its own terminal gate instead of relying on a single mixed-target overlay.
- Preserves the REL-FINAL boundary: source/static target seals do not claim signed artifact, device, operator, release-ready, or release-ops-ready evidence.

## Gradle/runner gate

- Target: `studyflow_release`
- Gate: `verifyOv30StudyflowReleaseTargetSeal`

## Verification

```bash
bash scripts/validate_ov30_studyflow_release_target_seal.sh
```
