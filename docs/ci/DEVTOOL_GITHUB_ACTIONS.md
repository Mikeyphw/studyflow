# StudyFlow — Devtool GitHub Actions integration

This is the repository-local Actions bridge for `~/Code/studyflow`. It does not modify existing
Devtool native Termux targets, Gradle convention plugins, personal signing, or app data.

## What is shipped

- `.github/workflows/devtool-android-build.yml`: **manual**, unsigned *release is forbidden*;
  Android debug APK or debug AAB for phone (`app`) and TV (`app-tv`), built on Linux with
  Java 21, Android 36/build-tools 36.0.0 and NDK `29.0.14206865`. Artifacts are
  verification-gated, SHA-256 logged, retained for seven days. APKs are checked with
  `apksigner`; AABs are rejected unless they pass strict `jarsigner` cryptographic verification (permitting self-signed debug certificates, but never unsigned members).
- `.github/workflows/studyflow-ci.yml`: push/PR/dispatch checks for the Android phone,
  TV, headless desktop, and web clients; APK output is uploaded on successful Android CI.
  The source contract runs before client jobs. Lanes run without signing secrets.
- `scripts/verify_studyflow_actions.py`: offline source/workflow integration checks.

This changes CI **configuration only**. A passing GitHub job is not on-device Android
acceptance, Play Store production signing, attested release qualification, or proof of
all historical repository gates.

## One-time activation (Termux/Zsh)

Apply the overlay from a **clean** StudyFlow Git worktree (source-bound safety).
After application, commit the four newly added files and push the branch.
GitHub only registers workflow YAML present in the remote repository. Workflow dispatch
from GitHub may additionally require a workflow file on the repository default branch;
if the workflow is not shown in `actions workflows`, merge the workflow definition first.

```sh
cd "$HOME/Code/studyflow"
python3 scripts/verify_studyflow_actions.py

git status --short
git add .github/workflows/devtool-android-build.yml \
  .github/workflows/studyflow-ci.yml \
  scripts/verify_studyflow_actions.py docs/ci/DEVTOOL_GITHUB_ACTIONS.md
git commit -m 'Enable StudyFlow builds and CI through Devtool GitHub Actions'
git push
```

Confirm token/gh login has sufficient access, and diagnose without dispatch:

```sh
devtool -r "$HOME/Code/studyflow" actions diagnose --json
devtool -r "$HOME/Code/studyflow" actions workflows --json
```

## Phone/TV build through Devtool

Change the `REF` variable to the branch that you committed and pushed. The examples
assume the branch contains the workflows and is dispatchable on GitHub.

```sh
REPO="$HOME/Code/studyflow"
REF="$(git -C "$REPO" branch --show-current)"

# Verify the workflow source and inputs remotely, without triggering a job.
devtool -r "$REPO" actions plan devtool-android-build.yml --ref "$REF" \
  --input project_dir=. --input module=app \
  --input variant=debug --input format=apk --json

# Launch phone APK build. Change module=app-tv for TV.
devtool -r "$REPO" actions run devtool-android-build.yml --ref "$REF" \
  --input project_dir=. --input module=app \
  --input variant=debug --input format=apk --yes --json

# For all four client lanes in CI:
devtool -r "$REPO" actions run studyflow-ci.yml --ref "$REF" \
  --input lane=all --yes --json
```

Devtool's generic **`actions android discover/plan/run`** currently cannot identify
StudyFlow's `id("studyflow.android.application")` convention plugin using its static
recognizer. **Use `actions plan` / `actions run` above**, which use the same authenticated
GitHub Actions dispatch provider. Do not add misleading plugin names to Gradle scripts.
Once a real GitHub dispatch completes, the **`actions android collect`** operation can
still verify a debug package from `devtool-android-build.yml`, because its collector
identifies the workflow by filename rather than using module discovery.

## Monitor, download, verify

The `actions run --json` output nests Actions-specific fields under `.data`. Dispatch
can be accepted without a confidently correlated `run_id` (HTTP 204); in that case
retain `request_id` and use `actions resume`, then inspect `actions runs` and explicitly
select the verified run ID. Do not blindly re-dispatch an uncertain request.

```sh
devtool -r "$REPO" actions runs --limit 10 --json
devtool -r "$REPO" actions view --run-id RUN_ID --json
devtool -r "$REPO" actions watch --run-id RUN_ID --interval 10 --max-checks 90 --json
devtool -r "$REPO" actions artifacts --run-id RUN_ID --json

# Import ZIP without extracting it implicitly:
devtool -r "$REPO" actions download --run-id RUN_ID --artifact-id ARTIFACT_ID --json

# Stronger verification and materialization for the *manual* Android workflow:
devtool -r "$REPO" actions android collect \
  --run-id RUN_ID --artifact-id ARTIFACT_ID \
  --format apk --variant debug --expected-package com.mikeyphw.studyflow \
  --download --json
```

For TV use `--expected-package com.mikeyphw.studyflow.tv`. For bundles use
`--format aab`; verification requires `jarsigner`/`keytool` and the Android build
verification environment. For APKs ensure `apksigner` and `aapt`/`aapt2` are present.
Artifact collection requires the GitHub run to have completed successfully and is
**not** a release qualification (`qualified_release=false`).

## Boundaries and limitations

- Only **debug builds** are offered remotely. Keep the existing permanent personal
  signing process on the device. No signing keys or passwords are uploaded to GitHub.
- GitHub runners can test/compile but do not replace real Android device tests.
- Source snapshots can include newer Gradle, SDK, or npm requirements; update both CI
  workflows and the static verifier together when changing those baselines.
- The repository has very broad Gradle quality gates. This CI executes concrete
  Android unit/lint tasks, desktop JVM tests, and web tasks; it does not assert that
  all other configured Devtool local validation phases ran.
- GitHub Actions workflow execution and artifact download were **not** performed while
  constructing this offline overlay. Watch real runs before trusting artifacts.
