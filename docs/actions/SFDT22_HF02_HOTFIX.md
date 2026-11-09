# SFDT22 HF02 — real plan wizard UI selector and sanitized failures

Source-locked to PR #15, branch `feature/sfdt22-real-study-workflows-20261107`, commit `c753ec02db3894150c0705f975b28ed3ccc01828`.

## Verified hosted result

On 2026-11-08, SFDT22 run 42434701370 compiled all app and test APKs, verified signers and exact source, and booted its disposable API36 emulator. It refused at `phase=plan`, zero verified tests. The minimal receipt reported only ValueError and no detailed reason. SFDT21 regression, Devtool phone/native CI, StudyFlow CI and SFDT11 all passed on the HF01 source SHA. This does **not** prove the proposed new matcher caused that failure; its diagnosis was hidden by log minimization.

## Exact fixes

1. The actual production `PlanEditorScreen` first step has two `OutlinedTextField` inputs in order: plan name, description. The HF02 test targets the first editable `SetText` action and explicitly checks two fields exist rather than assuming the Material 3 label merges into the editable semantics node. It still opens the real plan editor from the fresh Today workspace and creates a plan through real controls.
2. The plan test now wraps each real operation in a stage which **rethrows** failures with a fixed marker. No fake fixtures, injected repositories, or exception swallowing. This can locate a runtime failure to one of seven allowlisted plan stages.
3. The fail-closed emulator verifier classifies runner output to a short, stable `failure_reason_code`, `failure_test`, `failure_step`. Raw instrumentation stdout, exception stack, account contents and ADB/logcat are **not** saved. Failure still yields nonzero exit, no screenshots and no promotion.
4. Both modified source SHA-256 values are updated in the immutable-source workflow; 11 new negative-first offline tests are required.

This source-side fix is **unqualified until hosted API36 confirms all five real journeys, restart persistence, and seven unique images**. Keep mandatory emulator gate red on any mismatch. Release signing and native Devtool jobs are unchanged.
