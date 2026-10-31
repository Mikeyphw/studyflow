# REL-HOSTED-01 — GitHub unsigned candidate → private signing → device evidence

**Status:** at first hosted run 41207377489, phone unsigned candidate PASS, TV blocked by wrong JVM test task; REL-HOSTED-01-R1 corrects source, new-SHA hosted verification and device qualification pending.
**Baseline:** `92f0538a88491356373058cb3ddecf59b60cb434`, standard CI `41171083215` (5/5), deep CI `41171084358` (5/5).

## Security and evidence scope

- Gradle executes **only on GitHub Actions**. No local Termux/Ubuntu/Devtool Gradle calls.
- Existing `assembleRelease`/`bundleRelease` signing requirements remain intact. The workflow sets
  `STUDYFLOW_HOSTED_UNSIGNED_CANDIDATE=true` to expose a *different*, unsigned `hostedCandidate`
  build type only inside GitHub Actions with no signing configuration.
- GitHub never receives personal keystore, passwords, aliases, or signing properties. Checkout has
  `persist-credentials: false`, minimal read-only permissions, exact source SHA and no publishing.
- Phone and TV staging APK/AAB are unsigned, release-initialized, and not debuggable; TV retains
  release minification/shrinking. Both are only *candidates*, not published releases.
- All candidate signatures/identity are checked on GitHub; the offline signer rechecks them,
  exact per-file SHA-256, expected 40-hex source SHA, and GitHub run ID before signing.
- The signer emits `SIGNED_STAGE_ONLY`, never `PASS` in REL01 host/connected/journey/device reports.
  REL-FINAL must remain pending until legitimate connected signed-APK restore/restart and device
  evidence exists, plus operator checklist.

## Observed hosted evidence and R1 correction (2026-10-31)

- Source SHA `6178499c195952288f5e45981bf84c3601ceae3e`: regular CI
  [41207374268](https://github.com/Mikeyphw/studyflow/actions/runs/41207374268)
  and deep CI [41207374915](https://github.com/Mikeyphw/studyflow/actions/runs/41207374915)
  succeeded. Hosted run [41207377489](https://github.com/Mikeyphw/studyflow/actions/runs/41207377489)
  staged and uploaded the **phone** unsigned candidate, but **TV** failed selecting an
  unavailable `:app-tv:testReleaseUnitTest` task before TV packaging.
- REL-HOSTED-01-R1 selects `:app:testReleaseUnitTest` for phone and
  `:app-tv:testDebugUnitTest` for TV (the same selection already proven in
  standard CI), then still executes `lintRelease`, `assembleHostedCandidate`
  and `bundleHostedCandidate` for both. A separate `always()` artifact captures
  diagnostic reports even on failed hosted builds.
- **Do not download/sign as a complete phone+TV candidate set using run 41207377489**:
  it has no TV package. Dispatch the revised workflow at its **new commit SHA**
  and require both modules' candidate jobs to pass before private signing.

## 1. One-time personal key, in Termux (no Gradle)

```sh
pkg install openjdk-21 python
bash tools/setup-personal-signing.sh
bash tools/print-personal-signing-fingerprint.sh
```

If the key already exists, **do not regenerate or replace it**; keep a private backup of both
`~/.config/studyflow/android-signing.properties` and the referenced `.p12` outside the repo.
Also install Android SDK build tools containing `apksigner`, `zipalign`, and `apkanalyzer`. The script
accepts `--apksigner`, `--zipalign`, `--apkanalyzer`, `--jarsigner` for non-PATH tools.

## 2. Dispatch the exact GitHub commit

```sh
cd "$HOME/Code/studyflow"
git pull --ff-only
SHA="$(git rev-parse HEAD)"
REF="$(git branch --show-current)"
gh workflow run studyflow-hosted-release.yml -R Mikeyphw/studyflow --ref "$REF" -f expected_sha="$SHA"
gh run list -R Mikeyphw/studyflow --workflow studyflow-hosted-release.yml --limit 5
```

Wait for the workflow to **finish successfully**. It builds `app` and `app-tv` and uploads each
module's three files (`unsigned.apk`, `unsigned.aab`, `hosted-candidate-manifest.json`).
Do not mistake successful source-only tests for hosted artifact production.

## 3. Download and privately sign, separately for phone and TV

```sh
RUN_ID=REPLACE_WITH_SUCCESSFUL_GITHUB_RUN_ID
SHA="$(git rev-parse HEAD)"  # Must equal the source SHA used for RUN_ID.
mkdir -p "$HOME/studyflow-hosted-input"
gh run download "$RUN_ID" -R Mikeyphw/studyflow \
  --name "studyflow-hosted-app-unsigned-${RUN_ID}" \
  --dir "$HOME/studyflow-hosted-input/phone"
gh run download "$RUN_ID" -R Mikeyphw/studyflow \
  --name "studyflow-hosted-app-tv-unsigned-${RUN_ID}" \
  --dir "$HOME/studyflow-hosted-input/tv"
python3 scripts/studyflow_hosted_candidate.py sign \
  --bundle-dir "$HOME/studyflow-hosted-input/phone" \
  --out-dir "$HOME/studyflow-signed-${RUN_ID}-phone" \
  --expected-sha "$SHA" --expected-run-id "$RUN_ID"
python3 scripts/studyflow_hosted_candidate.py sign \
  --bundle-dir "$HOME/studyflow-hosted-input/tv" \
  --out-dir "$HOME/studyflow-signed-${RUN_ID}-tv" \
  --expected-sha "$SHA" --expected-run-id "$RUN_ID"
```

An existing output directory is deliberately rejected; no overwrite or auto-install. Do not put
signed artifacts or secrets into the repository, `.github`, Devtool overlay, or a public GitHub
artifact. The two signing receipts are written to `build/reports/studyflow/hosted-{app,app-tv}-signed-stage.json`
as **non-release** evidence, while actual signed packages stay in the selected private directories.

## 4. Acceptance is a separate phase

Run `python3 scripts/run_rel_final_release_campaign.py --mode plan` and review
`docs/release/FINAL_ACCEPTANCE_CHECKLIST.md`. The plan now shows hosted staging receipts but
retains `PENDING_TARGET_EXECUTION`. The old `run_rel_final_release_campaign.py --mode execute`
and `run_target_release_validation.py --mode automated` trigger Devtool local Gradle and are
**not supported** by the GitHub-only policy; do not run them.

To obtain actual `releaseReady=true` in future, add a separate GitHub-hosted connected/instrumented
release-test workflow and import verifiable exact-signed-APK restore/restart evidence. Continue
with actual device install/upgrade, permissions, widgets, accessibility, backup/restore,
TV Leanback/D-pad/cast tests and operator review. No staging command in this deliverable claims
those checks are complete.

## Failure/rollback rules

An unexpected signed input, source/run mismatch, missing build tools, absent or unsafe keystore,
malformed ZIP, package/version mismatch, failed cryptographic verifier, or stale output directory
blocks signing. Treat any failed GitHub candidate job as blocked; no fallback to debug or release
bypasses. The separate legacy release tasks continue to fail closed without required credentials.
