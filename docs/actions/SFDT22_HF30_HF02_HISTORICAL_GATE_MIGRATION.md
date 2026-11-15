# StudyFlow HF30 HF02 — inherited diagnostic-contract convergence

## Why the original HF30 HF01 refused

The HF30 focused workflow correctly moves from `--diagnostic-through session` (three real UI journeys) to `--diagnostic-through practice` (four journeys). The historical HF22 and HF26 contracts still asserted that the focused workflow must contain the obsolete session-scoped flag. The fail-closed disposable worktree preflight therefore stopped on `scripts/test_sfdt22_hf22_contract.py` even though the new workflow was intentional. HF26 would then have failed for the same reason.

## Changes

- Upgrade **both** inherited contracts to require exactly two `practice`-scoped invocations: `--run` and `--verify-diagnostic`.
- Continue asserting exact reviewed SHA, repository owner, no `pull_request_target`, no bypass, read-only Gradle caching, non-release receipt fields, and the separation from the full gate.
- Preserve `DIAGNOSTIC_THROUGH['session'] == 3` for legacy historical mapping while requiring `DIAGNOSTIC_THROUGH['practice'] == 4` for the expanded nonqualifying lane.
- Update the full hosted gate's **single mandatory SHA checks** of both changed contract files. Preserve and update **both mandatory** SHA checks of the focused workflow. No pin is removed.
- Add a new HF30 HF02 source regression proving exact run/verify scope, source SHA checks and nonqualifying status; insert its digest and execution into the full gate.
- Retain the original HF30 implementation of read-only completed-session SQL proof, permanent navigation dock, seven screenshots, five journeys, force-stop/new PID, Room and SFLP regressions.

## Operating rules

All source paths and the Git tree are pinned to HF29 SHA `8d92149f0d3b196d3afb81fd093185bdffdd1e30`. The Python/Rich ZIP launcher uses `$TMPDIR`, validates in an isolated Git worktree before native Devtool apply, and never force-pushes, merges, or rebases. The source-contract tests run; they are **not suppressed or skipped**.

After a new staging commit is created, all hosted tests still need to run. The four-journey focused lane **does not qualify** release; full Android 36 needs five real UI journeys, all seven images, Room/SFLP evidence and a new process ID after force-stop. PR #15 must remain unmerged until a separately reviewed acceptance decision.
