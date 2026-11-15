# SFDT22 HF39 HF01 — Physical ARM64 practice runner correction

**Repository:** `Mikeyphw/studyflow`, PR #16. **Source HEAD before HF39:** `de7e8d55872c1d0293350546ab26b938ec49091b`.

## Observed result on physical Android 16 / ARM64

The fresh APK pair from GitHub build `43669809377` installed and executed two real Room regressions and three SFLP regressions successfully. Three real production UI journeys passed. The fourth journey's Practice Start had progressed past the independent Room start assertion, then failed at `practice_runner_question`, initially mapped to `compose_assertion`.

The old journey executed an unscoped, immediate `visible(questionStatement)` before invoking its specific runner readiness helper. Practice's real `LazyColumn` inserted the active runner near the top while the viewport could remain scrolled to the Recent Tests Start button near the bottom. HF39 corrects production scrolling, switches the test to runner-specific readiness, checks real question and answer controls separately, and adds bounded failure classifications. It does **not** waive real UI, Room commits, five journeys, seven screenshots, physical source/signer checks, or cold-process restart.

## HF01 legacy-contract correction

The first HF39 application correctly rolled back after the unchanged HF33 regression
contract rejected the alias `hasAnyAncestor(runner)` in the new Compose readiness
helper. HF01 preserves the same exact ancestor matching and requires the explicit
`hasAnyAncestor(hasTestTag("studyflow.practice.runner"))` expression in both real
question and answer selectors. It also validates this inherited contract before
modifying source files; all original HF33 tests remain enabled. The aborted run
changed no committed files and never pushed.

## Applying

```zsh
ZIP="$HOME/storage/downloads/StudyFlow_HF39_HF02_COMPLETE_ARCHIVE_FIX_20261115.zip"
python3 "$ZIP" --self-test
python3 "$ZIP" --inspect
python3 "$ZIP" --dry-run
python3 "$ZIP"
```

The launcher operates only on a clean source-pinned checkout. It checks the exact PR branch and Git blob hashes, updates six allowlisted paths, executes inherited offline source contracts, and commits/pushes only if they pass. No local Gradle, device writes, force push or merge. After push, GitHub Actions builds the exact new APK pair. **The previous build cannot qualify the new source commit.**

## Device rerun safety

The previous instrumentation run created StudyFlow data on the device. Simply rerunning all five tests against that installation can create duplicate plans and can only yield diagnostic-only qualification. The runner does not automatically delete user data or replace mismatched APKs.

For full fresh-device qualification, use a disposable StudyFlow installation with no pre-existing user data. Clear or uninstall it only after explicitly deciding that its data is expendable. The pre-existing StudyFlow APK from the prior source commit must not be silently replaced: the physical runner intentionally refuses a mismatched installed APK. Once the old disposable app and its test APK are intentionally removed, the new source-matched APK pair can be installed automatically.

To inspect packages and avoid unintended deletion:

```zsh
adb -s localhost:5555 shell pm list packages com.mikeyphw.studyflow
python3 -B scripts/sfdt22_physical.py --preflight --serial localhost:5555
```

**Destructive manual reset — only for a truly disposable StudyFlow test installation:**

```zsh
adb -s localhost:5555 uninstall com.mikeyphw.studyflow.test
adb -s localhost:5555 uninstall com.mikeyphw.studyflow
```

Uninstalling StudyFlow destroys its app-private data. Do not run these commands on an installation you need to preserve. Neither HF39's launcher nor its physical runner performs the uninstall automatically.

After the fresh matching GitHub APK build succeeds, execute:

```zsh
cd ~/Code/studyflow
python3 -B scripts/sfdt22_physical.py --preflight --serial localhost:5555
python3 -B scripts/sfdt22_physical.py --run --serial localhost:5555 --accept-device-tests
```

The runner selects an exact-source successful GitHub Actions build via `gh`, verifies manifest/APK hashes, installs both APKs, then checks independent Room/SFLP regression methods, five production UI journeys, a real process restart, and seven distinct screenshots. On failure it writes a bounded JSON report to `~/.local/share/studyflow/physical-sfdt22/SFDT22_PHYSICAL_EVIDENCE.json` without retaining raw instrumentation logs or private study data.

## Classification

New `practice_runner_question` failure reasons: `practice_runner_root_missing`, `practice_runner_question_offscreen`, `practice_runner_answer_offscreen`, `practice_runner_answer_disabled`. Existing `practice_runner_question_missing`, `practice_runner_answer_missing`, `practice_runner_not_ready`, and ambiguity reasons remain valid.

The SFDT22 hosted emulator remains paused. SFDT21 hosted real UI, regular CI, and the SFDT22 physical APK build-only workflow remain active.

## HF02 ZIP payload correction

HF39 HF01 failed before writing repository files because the launcher requested
`payload/docs/actions/SFDT22_HF39_HF01_PHYSICAL_RUNNER_HANDOFF.md` but the ZIP
contained the unversioned `SFDT22_HF39_PHYSICAL_RUNNER_HANDOFF.md`. HF02 ships
the correct exact path and verifies the complete four-member ZIP inventory,
CRC and handoff contents before switching branches or altering source files.
This is a packaging fix; the real Android acceptance remains pending.
