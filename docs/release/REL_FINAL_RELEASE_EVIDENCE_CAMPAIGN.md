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

## GitHub-only candidate bridge (REL-HOSTED-01)

The older `--mode execute` and REL01 `--mode automated` paths still use the **local**
`studyflow_release` Devtool Gradle target and must **not** be run in the current
GitHub-only Gradle workflow. Those commands do not become evidence simply because a
candidate APK has been downloaded. Use `studyflow-hosted-release.yml`, the standalone
`studyflow_hosted_candidate.py sign` command, and the resulting
`build/reports/studyflow/hosted-{app,app-tv}-signed-stage.json` records instead.

The GitHub job produces unsigned, non-debuggable APK/AAB candidates from separate
`hostedCandidate` build types. It never receives keys. The offline signer validates
source SHA, run ID, package identity, unsigned state and SHA-256 for both artifacts;
then signs privately with the existing permanent key. This is a staging bridge **only**:
REL01 connected suites and exact-signed-APK restore/restart, REL03 install/upgrade,
manual device checks and REL07/REL08 operator evidence remain unfulfilled. Never
copy the staging receipt into `release-host-gates.json`, `connected-test-gates.json`,
`full-journey.json` or `final-release-verdict.json`. See
`docs/release/REL_HOSTED_01_GITHUB_CANDIDATE_AND_OFFLINE_SIGNING.md`.

## Safety boundary

The repository release tools do not upload, publish, sign with embedded secrets, delete user data, downgrade devices, or perform rollback automatically. Signing credentials stay external. REL06 emits rollback planning evidence and commands only.
