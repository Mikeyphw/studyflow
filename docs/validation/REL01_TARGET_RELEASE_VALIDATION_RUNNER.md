# REL01 target release validation runner

REL01 adds `scripts/run_target_release_validation.py`, a small orchestration helper for the final
StudyFlow release evidence pass. It exists because the roadmap after SFLP24 calls for target-machine
evidence, not another source-only protocol feature.

## Modes

```bash
python3 scripts/run_target_release_validation.py --mode plan
python3 scripts/run_target_release_validation.py --mode automated
python3 scripts/run_target_release_validation.py --mode device-template
python3 scripts/run_target_release_validation.py --mode verdict
```

`plan` writes a target-machine plan and reports missing prerequisites as `PENDING_TARGET_EXECUTION`.
`automated` delegates to `scripts/run_final_release_acceptance.sh`, which owns the signed APK/AAB,
connected Android, and restore/restart evidence chain. `device-template` writes the manual evidence
command template without marking anything as passed. `verdict` runs the existing fail-closed
`verifyFinalReleaseVerdict` gate.

## Devtool application

REL01 is an intermediate operational overlay unless you explicitly decide to treat the next run as the
final target validation. Apply with:

```bash
devtool --yes --copy -r ~/Code/studyflow --target studyflow_android \
  apply overlay /sdcard/Download/studyflow_rel01_target_release_validation_runner_v1.zip \
  --no-validate --resource-profile standard --max-workers 4 --cpu-limit 4 \
  --gradle-heap-mb 2048 --gradle-metaspace-mb 768 --memory-guard-mb 0 --parallel
```

The static verifier is `verifyRel01TargetReleaseValidationRunner`.

## REL02 handoff

REL01 produces or requests the target-machine evidence. REL02 consumes that evidence and exports `REL02_RELEASE_EVIDENCE_EXPORT` outputs: a summary JSON, a triage JSON, a markdown report, and a shareable evidence bundle.
