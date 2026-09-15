# OV30 studyflow_web — Web/PWA terminal target seal

Overlay 30C of the terminal **Overlay 30 of 30** remediation split seals the `studyflow_web` Devtool target independently.

## Delivered

- Adds a target-scoped executable verifier: `scripts/verify_ov30_studyflow_web_target_seal.py`.
- Adds a target-scoped validator entry point: `scripts/validate_ov30_studyflow_web_target_seal.sh`.
- Wires the target to its own terminal gate instead of relying on a single mixed-target overlay.
- Preserves the REL-FINAL boundary: source/static target seals do not claim signed artifact, device, operator, release-ready, or release-ops-ready evidence.

## Gradle/runner gate

- Target: `studyflow_web`
- Gate: `verifyOv30StudyflowWebTargetSeal`

## Verification

```bash
bash scripts/validate_ov30_studyflow_web_target_seal.sh
```
