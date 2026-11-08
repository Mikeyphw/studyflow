# StudyFlow SFDT22 HF03 — Real touch and granular plan-open regression

This consolidated, additive hotfix targets PR #15 at exactly
`22ddfb6d743a936ce8656f05a92ff60a8d04a624`.

## Hosted evidence

SFDT22 run 42436498116 compiled the production debug APK and instrumentation APK,
verified their signing and hash, and launched an API36 emulator. The first
journey failed with `failure_step=plan_open`, `failure_reason_code=compose_wait_timeout`.
All other ordinary and SFDT21 workflows passed on this same branch.

## Changes

- Split plan startup into plan_shell, plan_open, plan_editor_ready, and plan_name.
- Tap the real, visible Text child of the production Material3 `Button` via
  Compose `performTouchInput { click() }`, not a merged click-and-text selector.
- Keep source-specific allowlisted failure metadata, no raw logs or user content.
- Pin hosted source hashes and run HF03 negative-first regression tests.
- Maintain all five real journeys, seven screenshots, forced process restart,
  actual APK signature/byte checks, and fail-closed UI gate.

This is a portable source-pinned installer, **not** a native Devtool overlay.
Gradle and Android instrumentation are hosted-only on GitHub Actions; offline
source tests are not proof that the real emulator passed.

## Apply from Termux (zsh-safe)

```zsh
unzip -q /sdcard/Download/StudyFlow_SFDT22_HF03_Consolidated_Hotfix_Overlay.zip -d /sdcard/Download/SFDT22_HF03
cd ~/Code/studyflow
git switch feature/sfdt22-real-study-workflows-20261107
zsh /sdcard/Download/SFDT22_HF03/APPLY_SFDT22_HF03.zsh --repo "$PWD" --push
```

The installer refuses an unexpected source commit, branch, origin URL,
non-clean worktree, source digest drift, or an existing target file. It runs
HF01, HF02, HF03, the base SFDT22 and SFDT21 offline contracts, plus other
available source tests, commits and pushes to PR #15 without force pushing.
If the source changed, do not bypass preflight; create a freshly pinned patch.

Do not merge until the new SFDT22 real emulator UI gate is green.
