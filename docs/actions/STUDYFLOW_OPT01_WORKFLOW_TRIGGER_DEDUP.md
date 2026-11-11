# StudyFlow OPT01 — Deduplicate hotfix push / PR workflows

## Scope

This source-pinned OPT01 patch is folded into the HF07 Android diagnostics rebuild,
for existing **PR #16** at `add880ecc7c284209a9196b799f258877bd70e2f`.

- The SFDT26 push wrapper runs automatically only on true staging refs named
  `sfdt22-hotfix/*-staging-*` (the current staging ref is
  `sfdt22-hotfix/hf25-staging-20261109`). A normal PR feature branch
  (`sfdt22-hotfix/hf30-hf04-pr-20261110`) no longer triggers an extra focused
  API36 session for the same SHA.
- Targeted CI retains `pull_request`, `workflow_dispatch` exact-SHA and
  `push` on `main` and true `sfdt22-hotfix/*-staging-*` branches. It no longer
  runs both push AND PR builds on the same feature PR commit.
- PR #16 still gets Targeted CI, SFDT21, full SFDT22, SFDT11 source/trust,
  Devtool CI and ordinary StudyFlow CI. The focused diagnostic workflow
  remains available by manual exact-SHA dispatch / PR label, and by staging
  push via the SFDT26 wrapper.
- The launcher PR watch correctly expects six workflows, not the staging-only
  seventh; it keeps the real `gh run watch` TUI and `gh pr checks --watch`.

## What is not changed

No UI test removal, no Android 36 downgrade, no Room/SFLP bypass, no
seven-screenshot or force-stop/new-PID bypass, no secrets or release access.
**Offline contracts are not release qualification**. The five real SFDT22
journeys remain mandatory. HF07 failure reporting still categorizes build,
Room, runtime and per-stage UI failures.

## Operational scope

Only the named staging-branch convention triggers the automatic focused run;
non-staging experimental branch pushes will get targeted compilation when
opened as PRs or explicitly dispatched with a pinned SHA. Main receives its
normal push gate. If a new staging branch is named differently, update the
branch convention deliberately rather than broadening the auto-emulator trigger.

The pre-existing historical HF26 source contract is migrated, not removed,
and the full source-contract workflow updates all exact SHA-256 pins.
PR #15, staging HEAD, and main are not edited; this overlay appends a regular
commit to existing PR #16 only, after native Devtool source preflight.
