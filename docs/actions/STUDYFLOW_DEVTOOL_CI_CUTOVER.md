# StudyFlow Devtool CI activation (single workflow cutover)

## Qualification

On merged StudyFlow commit `c323f7f166a51f88a16caf40d2a48d7c526455f3`,
GitHub Actions run [42342359813](https://github.com/Mikeyphw/studyflow/actions/runs/42342359813)
passed **Devtool CI gate** and all four hosted jobs (phone, TV, Desktop, Web).
It uploaded the phone and TV APKs and their reports. The previous PR
qualification run 42341315525 also passed.

## Activation scope

- `.github/workflows/studyflow-devtool-primary.yml` remains the routine PR and
  `main`-push build/test workflow. Do not alter its executor or trust boundary.
- `.github/workflows/studyflow-ci.yml` remains available for **manual rollback**:
  `workflow_dispatch` supports `lane=all,android,desktop,web,deep` and exact
  `expected_sha`. `source` still runs for routine PR/push; Web/Desktop/Android
  builds now require `workflow_dispatch`. `deep-contracts` was already opt-in.
- Signing/release, `.devtool.toml`, native Devtool DAG and source verification
  scripts remain otherwise unchanged. No local Gradle.
- SFDT11's **exact blob pin** is updated to the reviewed legacy workflow bytes;
  the source policy is not disabled.

## Required-check setting: separate GitHub administrator action

This source overlay **cannot** edit branch protection. The connected GitHub App
could not read protection (`403 Resource not accessible by integration`). After
merging, inspect GitHub repository Settings > Rules > Rulesets / Branches for
any required legacy build check. Require **`Devtool CI gate`** in the new
workflow. Keep any desired signing/security/source checks; remove obsolete
legacy build checks if listed. If no branch rule exists, create one as needed.
Do not claim branch enforcement without verifying the setting.

## Hosted acceptance

On the cutover PR, confirm `Devtool CI gate` green, SFDT11 green and the
`StudyFlow CI` `source` status green; the legacy Web/Desktop/Android build jobs
should be **skipped** on PR and push. Confirm the Devtool phone/TV APK artifacts.

## Rollback

The prior legacy workflow is still manually dispatchable with an exact SHA:

```zsh
gh workflow run studyflow-ci.yml -R Mikeyphw/studyflow --ref main \
  -f lane=all -f expected_sha="$(gh api repos/Mikeyphw/studyflow/commits/main --jq '.sha')"
```

If the primary Devtool workflow regresses, revert the cutover commit/PR (which
restores legacy automatic build conditions and original SFDT11 pinned blob)
and adjust required status checks accordingly. Never disable signing/security
checks to bypass a failed CI.
