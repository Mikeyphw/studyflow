# StudyFlow SFDT22 HF05 — Canonical all-plans real-UI journey

Source-pinned to `Mikeyphw/studyflow` PR #15, exact branch commit `89cd8ee30658bb23cd6653e8f270b7999f16bcfa`.

## Verified failure

HF04 hosted run 42442920054 built the production and test APKs and launched the API36 emulator, but failed first test `realUserCreatesAStudyPlan` with `failure_step=plan_open`, `failure_reason_code=compose_wait_timeout`. `plan_shell` passed. The no-plan-only button was never found, despite a stable tag. This is consistent with the initial workspace not being the assumed no-plan screen; the evidence cannot identify why.

## HF05 approach

Use a real UI route that does not require an empty Today workspace: **Today → More → All plans → Create plan (FAB)**. All transitions invoke the production handlers, with tagged System Hub row and Plan List FAB, then the existing real Plan Editor, real domain writes, screenshots, cold restart and persistence test. No tests inject repositories, navigate directly via code, or fabricate a passed emulator result.

Independent `plan_system_hub`, `plan_all_plans` and `plan_open` failure stages are allowlisted for sanitized receipt diagnostics. All five real user journeys and seven screenshots remain mandatory.

## Termux / Zsh

Download `StudyFlow_SFDT22_HF05_Consolidated_Hotfix_Overlay.zip` to `/sdcard/Download`, then:

```zsh
unzip -q /sdcard/Download/StudyFlow_SFDT22_HF05_Consolidated_Hotfix_Overlay.zip -d /sdcard/Download/SFDT22_HF05
cd ~/Code/studyflow
git switch feature/sfdt22-real-study-workflows-20261107
zsh /sdcard/Download/SFDT22_HF05/APPLY_SFDT22_HF05.zsh --repo "$PWD" --push
```

This archive is a **consolidated source-pinned portable installer**, not native Devtool `apply-overlay`. The installer checks exact original Git blobs, origin, branch, commit and clean worktree. New UI identifiers are inserted into the existing real production screens by exact anchor, without modifying UI layout. It runs offline tests, commits, and pushes the original PR branch without force. GitHub Actions still owns Gradle and emulator execution. Leave PR #15 open until SFDT22 passes.
