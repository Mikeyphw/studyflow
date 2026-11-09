# StudyFlow SFDT22 HF04 — Stable production Create Plan action

**Target:** `Mikeyphw/studyflow`, open PR #15, branch `feature/sfdt22-real-study-workflows-20261107` at **exactly** `fac8494707bf681c6e6f21650b480cc0f0acea55`.

## Confirmed hosted failure

Run 42439919846 built both real APKs, launched Android 16 (API 36), and failed the first instrumentation method with `failure_step=plan_open`, `failure_reason_code=compose_wait_timeout`; none of the other four journeys ran. This is after the `plan_shell` step succeeded. All other workflows on this commit passed.

HF03's real-touch helper waits for `onAllNodes(hasText(label), useUnmergedTree = true).size == 1`. That unstable text-node-count requirement is independent of whether the production Material3 button exists and can make the real test time out. It is removed.

## Fix

- Adds optional `actionTestTag` to the production `StudyFlowEmptyState`, passed *only* by `WorkspaceNoPlanScreen` and applied directly to its existing Material3 `Button`.
- The hosted Compose test finds this exact production button with `onNodeWithTag`, requires it to be displayed, and performs its **real** `performClick()`; no fake screens, deep links, test-only navigation routes or database injection.
- Preserves all five hosted user journeys, their hard evidence gate, seven distinct screenshots, on-disk persistence and real process restart checks.
- Updates the HF03 test to reflect the stronger locator, adds 12 HF04 contracts, pins modified UI source SHA-256 values in the hosted source check.

The patch preserves the normal UI's appearance and all non-tagged `StudyFlowEmptyState` call sites.

**Qualification:** source checks can be performed locally in Termux; Android Gradle/build/instrumentation remain **GitHub Actions only**. Keep PR #15 open until all required hosted checks pass. Offline passing does not imply the Android 16 emulator passed.

## Apply to existing PR #15 (Termux / Zsh)

```zsh
unzip -q /sdcard/Download/StudyFlow_SFDT22_HF04_Consolidated_Hotfix_Overlay.zip -d /sdcard/Download/SFDT22_HF04
cd ~/Code/studyflow
git switch feature/sfdt22-real-study-workflows-20261107
zsh /sdcard/Download/SFDT22_HF04/APPLY_SFDT22_HF04.zsh --repo "$PWD" --push
```

The source-pinned installer verifies source commit, branch, origin, clean worktree and exact blob SHAs. It applies five complete source files and two new files, runs the pre-existing and new offline checks, commits and pushes without force. It refuses a changed source instead of rebasing automatically. This is a portable consolidated overlay installer, not a native Devtool overlay archive.
