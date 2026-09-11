# REL-FINAL five-client release evidence campaign

REL-FINAL is the terminal evidence campaign after PARITY04. It does **not** add product scope, Phase 65, or SFLP25.

## Roadmap promise audit

`docs/roadmap/REL_FINAL_PROMISE_AUDIT.json` records every pre-REL-FINAL verifier present at the post-PARITY04 source boundary, the imported connectivity/release promise ledger, and the gaps repaired by REL-FINAL. The exhaustive audit runs 211 static/product verifier scripts and separately proves that the two evidence-only release gates fail closed when their inputs are absent.

Run:

```bash
python3 scripts/run_rel_final_roadmap_audit.py
```

A PASS means product/source roadmap promises are closed. It is **not** release readiness.

During overlay application this exhaustive audit runs in artifact validation before Gradle compilation. Do not co-schedule it with a large parallel Gradle build: historical aggregate verifiers recursively execute child verifiers and are intentionally isolated from toolchain load. The Gradle terminal seal consumes the permanent promise ledger and runs compile/test/package/current-product gates instead.

## Evidence campaign

Prepare the target-host/device plan without claiming readiness:

```bash
python3 scripts/run_rel_final_release_campaign.py --mode plan
```

On the target Android build host, with release signing environment configured and exactly one intended adb device connected, execute:

```bash
python3 scripts/run_rel_final_release_campaign.py --mode execute --channel production
```

The automated Android gate is delegated to `devtool --target studyflow_release build`. The dedicated Android target uses Termux AAPT2, NDK 29, and `--no-configuration-cache`; it preserves the full signed host/unit/lint/assemble/bundle/connected-test/restore-restart gate while excluding the unrelated repository-wide `verifyFormatting` backlog. `run_final_release_acceptance.sh` must not invoke raw Gradle.

If the Devtool automated gate fails, the campaign stops immediately and records `BLOCKED`; it does not continue generating later evidence as if the failure were merely pending manual observation.

The automated portion binds signed APK/AAB bytes, connected tests, and the restore/restart journey to one validation session. It intentionally cannot fill the human observation checklist. Complete the generated device-acceptance and REL07 operator-checklist templates with real observations, then run:

```bash
python3 scripts/run_rel_final_release_campaign.py --mode finalize --channel production
```

REL-FINAL closes only when `final-release-verdict.json` has `releaseReady=true` **and** `release-ops-final-seal.json` has `releaseOpsReady=true`. Missing signing credentials, missing/extra adb devices, failed connected tests, absent restart/restore evidence, unchecked device observations, or incomplete operator review keep the campaign pending/blocked.

## Safety boundary

The repository release tools do not upload, publish, sign with embedded secrets, delete user data, downgrade devices, or perform rollback automatically. Signing credentials stay external. REL06 emits rollback planning evidence and commands only.
