# StudyFlow HF38 HF02 — Physical ARM64 SFDT22 handoff (HF36 + HF37 + HF38 consolidated)

**Status:** HF38 HF02 whitespace-safe replacement ready at pinned PR #16; physical acceptance **not yet run**.
**Repository:** `Mikeyphw/studyflow` (private).
**Pinned source:** `fac11e29924af37b5d4c39c1d60f194aefb0ac03`.
**Target branch:** `sfdt22-hotfix/hf30-hf04-pr-20261110`.
**Default Termux checkout:** `~/Code/studyflow`.
**Physical device:** `localhost:5555`, Android API 36, ABI `arm64-v8a`, non-emulator.

## What changed in HF38 HF01

- **One self-contained overlay:** Includes HF36's real UI Start-tap guard and HF37's paused hosted tests, build-only APK workflow, physical verifier, and source contracts; you do not have to apply HF36 or HF37 individually.
- **Safe automatic branch switching:** Launcher checks origin and clean worktree, `git fetch`es the exact pinned PR branch, switches or creates its local tracking branch, and only fast-forwards. It rejects modified or untracked files, stale/mismatched remote HEADs, non-fast-forward histories, detached HEADs, unsafe repos, staged changes, and source hash drift. It does **not** reset, rebase, force-push or auto-stash; successful application stays on the PR branch. `--inspect` is read-only; `--dry-run` may switch branches but doesn't modify source files or devices.
- **Hosted emulator disabled:** SFDT22 full and focused emulator jobs require a manual `workflow_dispatch` and the explicitly enabled repository variable `STUDYFLOW_SFDT22_HOSTED_ENABLED=true`. Ordinary PR and push events do not execute these SFDT22 emulator jobs; source-only checks remain live, and disabled hosted gates do **not** produce a positive qualification. SFDT21 remains unchanged.
- **GitHub build only:** Workflow `studyflow-sfdt22-physical-apks.yml` automatically builds `:app:assembleDebug :app:assembleDebugAndroidTest` on the PR branch, then publishes an app APK, an instrumentation APK and a SHA-256/signing/source manifest. No local Gradle, no GitHub emulator and no GitHub device test.
- **Automatic instrumentation APK:** In physical `--run`, the runner uses `gh` to discover the successful exact-source build-only run, downloads the matching artifact and validates all APK hashes and signer identities. When both app/test packages are absent it installs both. When the app is present, it installs **only** a missing instrumentation APK if the existing app's installed APK SHA-256 equals the exact built app SHA-256. An existing different app/test APK is **never** replaced or uninstalled; data is never wiped. Any run touching existing app data is **DIAGNOSTIC_ONLY**, even after successful instrumentation. The `--apk-dir` option remains for manually downloaded exact-source artifacts.
- **Existing instrumentation app:** Read-only preflight checks the exact `com.mikeyphw.studyflow.test/androidx.test.runner.AndroidJUnitRunner` target. A generic instrumentation controller targeting another package is **not** the StudyFlow test APK.

## HF38 HF01 correction retained: repeated focused-workflow source pin

The prior HF38 ZIP failed with `ANCHOR_DRIFT ... count=2` because its
embedded HF37 patcher expected the focused workflow SHA-256 line only once,
but the original full SFDT22 workflow intentionally verifies that same file
in **two** source-manifest sections. HF38 HF01 requires exactly two matching
original pins and updates **both** atomically. It refuses zero, one, or three
pins and verifies the new hash occurs exactly twice, without leaving the old
hash in either section. The staged HF37/HF38 source contracts now enforce
that invariant. No Android test result was obtained from the refused run.

If the original HF38 switched you from `main` to the pinned PR branch, leave
that branch selected. The replacement overlay detects it and proceeds;
there is no reason to switch back to `main` or run HF36/HF37 separately.

## HF38 HF02 correction: staged Markdown whitespace

HF38 HF01 correctly switched to the pinned PR branch but then refused to commit
because five Markdown metadata lines in this handoff used two trailing spaces.
`git diff --cached --check` treats these as errors. HF38 HF02 removes those
trailing spaces, preserves both workflow SHA pins, and checks all newly added
lines for trailing whitespace **before writing repository files**.
The overlay also stages its complete generated change in a disposable Git
repository and runs the same `git diff --cached --check` in `--self-test`.
The previous refusal rolled back the source changes; if the PR branch is already
checked out, leave it selected. No need to apply HF36/HF37/HF38 separately.

## 1. Apply safely from `main` or from the existing PR checkout

```zsh
ZIP="$HOME/storage/downloads/StudyFlow_HF38_HF02_STAGED_WHITESPACE_20261114.zip"
python3 "$ZIP" --self-test
python3 "$ZIP" --inspect
python3 "$ZIP" --dry-run    # switches to PR branch if safe; no source/device edits
python3 "$ZIP"              # full source checks, 10-file allowlist, commit, push
```

A local `main` at SHA `6e54256befc2f17a9886ebd0ff461e9de8153088` is acceptable if clean. Launcher checks the **remote PR branch** against the exact expected SHA before switching. It stops on a dirty worktree or divergent branch without overwriting it.

GitHub pushes are checked against exact branch/HEAD, never force-pushed. The launcher does not build or install APKs on the device.

## 2. Read-only inspection of physical device and installed instrumentation

```zsh
cd ~/Code/studyflow
python3 -B scripts/sfdt22_physical.py --preflight --serial localhost:5555
```

Reports Android 16, ARM64, whether the target app and exact StudyFlow test package are installed, and whether the specific instrumentation runner is registered. It performs no device writes.

## 3. Automatically download exact source build and install matching instrumentation

**Only on a disposable installation or when StudyFlow data can be modified**. The full suite creates plans, questions, practice tests and review data. Install/authenticate `gh` to access the private repository first (`gh auth status`). A successful build-only run for your **exact local HEAD** must exist.

```zsh
cd ~/Code/studyflow
python3 -B scripts/sfdt22_physical.py --run \
  --serial localhost:5555 \
  --accept-device-tests \
  --output-dir "$HOME/.local/share/studyflow/physical-sfdt22"
```

`--run` now automatically discovers and downloads the matching GitHub APK pair. It automatically installs the missing APKs after checking their source SHA, manifest SHA-256s, app/test signing-certificate parity, package identity, and installed APK digests. You **do not** need `--install-apks` (accepted only as a compatibility flag).

If StudyFlow or its test package already exists, you must explicitly accept writes to that existing app data. For an identical already-installed APK, the command is:

```zsh
python3 -B scripts/sfdt22_physical.py --run \
  --serial localhost:5555 \
  --accept-device-tests --accept-existing-data
```

The runner then installs the **missing matching test APK** automatically if necessary. It never silently replaces an existing different-signed or differently hashed app. If you get `INSTALLED_APP_HASH_MISMATCH_NO_REPLACEMENT`, use a separate disposable device/user environment or back up and manage the conflicting installation yourself; this launcher will not uninstall it.

To use a previously downloaded artifact directory instead of auto-download:

```zsh
python3 -B scripts/sfdt22_physical.py --run \
  --serial localhost:5555 \
  --apk-dir "$TMPDIR/studyflow-physical-apks" \
  --accept-device-tests
```

## 4. Optional: use a pre-existing StudyFlow instrumentation installation

```zsh
python3 -B scripts/sfdt22_physical.py --run-existing \
  --serial localhost:5555 \
  --accept-device-tests --accept-existing-data
```

This mode only runs when the existing APK registers the exact StudyFlow test runner. It does **not** download or install, and can never claim a source-qualified physical pass. It will execute real production UI mutations on existing data. An unrelated instrumentation-controller app is not a substitute.

## Acceptance and reporting

The physical runner requires **2 Room tests + 3 SFLP tests + 5 real MainActivity UI journeys + true force-stop/new PID + 7 unique PNG screenshots + source-of-truth boundary/probe evidence**, using the exact instrumentation test methods already present in SFDT22. A stale APK, mismatched SHA, wrong signer, wrong device/ABI, duplicate screenshots, missing Room proof, or failing test is **REFUSED**. There is no host-log or screenshot bypass.

- Fresh install of both exact-source APKs, complete proof: `PHYSICAL_PASS` and `physical_device_qualified=true` (not a release or publication qualification).
- Existing installed app/test data or `--run-existing`: `DIAGNOSTIC_ONLY_PASS` at best.
- `SFDT22_PHYSICAL_EVIDENCE.json` and locally saved screenshots remain under `~/.local/share/studyflow/physical-sfdt22`. Raw instrumentation logs and private study data are not uploaded.

The GitHub source-only checks and physical build-only workflow are **not** acceptance passes. Once physical acceptance is complete, resume the original StudyFlow roadmap: OPT02 shared APK build/caching in shadow mode, then OPT03 Devtool/SDK/cache optimization. Hosted emulator testing can be resumed only via deliberate repository-variable plus manual dispatch.
