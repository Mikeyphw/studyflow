# SFDT22 HF15 — GitHub Actions failure remediation

Pinned to PR #15 commit `1d7e0054f9b7c13ad5e3e06ff182cf8fdf644ceb`.

## Confirmed blockers

- StudyFlow CI runs `42512851455` and `42512856148` fail the OV01 S0-05 immutable historical root manifest contract. Devtool modified `devtool-overlay.json` in HF13. This patch restores the exact verified version from parent `cf6d116c`, without weakening the policy.
- SFDT22 run `42512856050` confirms `phase=subjects`, `stage=topic_expand`, `reason=subject_not_expanded`; one journey complete, remaining journeys and restart unverified. This does not prove a failed database write. The current physical tap conflates failed interaction and failed UI transition.

## Changes

A uniquely scoped production `TextButton` semantic click precedes any physical fallback; it must switch its real tag from Expand to Collapse. The result of the first click is retained as evidence for a distinct sanitized reason code if fallback fails. Before a single fallback, the test ensures the saved subject is still uniquely collapsed and all subject/topic success and failure Snackbars have cleared. The original mandatory persisted-topic-row assertion remains.

A source test verifies the failure mappings, single pointer fallback, five real tests, actual force-stop/new PID, seven screenshots and GitHub source SHA pins. The workflow is updated to execute this source test without diluting emulator success requirements.

The native Devtool overlay contains only code/tests/workflow/doc. Its incidental root metadata mutation is reversed by the installer using verified historical content. Source contracts run in a disposable git worktree *before* actual Devtool apply and again after apply, before commit. Git operations refuse dirty/wrong-branch/unexpected SHA and never force push.

## Qualification boundary

GitHub hosted `api-level:36` instrumentation and main branch `:app:packageDebug` must still be observed. The main packaging failure on run `42424780036` has no exposed nested exception; current PR Devtool phone packaging succeeded, so there is no verified source fix for that historical incident. No local Gradle execution and no physical/release qualification.
