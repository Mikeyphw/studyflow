# StudyFlow Devtool CI — simple primary workflow candidate

**Phase: hosted acceptance, not yet replacement of required `StudyFlow CI`.**

StudyFlow's Gradle builds are already executed by GitHub Actions, never Termux.
This patch migrates orchestration to the native Devtool DAG implementation and
retains a single source-controlled PR/push CI workflow with four parallel lanes.

| Hosted job | Devtool DAG | Effective tasks | Notes |
| --- | --- | --- | --- |
| Phone | `ci-sfdt07-phone-debug` + `ci-sfdt08-phone-release-jvm` | `:app:assembleDebug`, `:app:lintDebug`, `:app:testReleaseUnitTest` | Two Devtool-owned first-class Android jobs; guarded phone Gradle silence |
| Android TV | `ci-sfdt07-tv-debug` | `:app-tv:assembleDebug`, `:app-tv:testDebugUnitTest`, `:app-tv:lintDebug` | First-class Android Gradle job |
| Desktop | `ci-sfdt08-desktop-fast` | `:desktopApp:classes`, `:desktopApp:test` | Existing Devtool-owned Python-shell Gradle bridge; **not yet first-class JVM provider** |
| Web | `ci-sfdt08-web-full` | npm test/lint/build/release:check + pytest | Existing Devtool-owned Python-shell Node bridge; **not yet first-class Node provider** |

## Deliberate scope

- New `.github/workflows/studyflow-devtool-primary.yml` runs on PRs and `main` pushes, plus manual dispatch.
- `scripts/studyflow_devtool_primary.py` calls Devtool's existing `compile_graph` and `run_node` APIs; no parallel scheduler, new Gradle subprocess implementation, or alternate task config.
- Devtool source pinned to `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`, used in prior successful SFDT08 hosted fast/deep evidence. Later runtime upgrades require review.
- Phone's native Gradle worker is supervised by the bounded SFDT18-HF01 monitor (10-minute substantive-output timeout, 45-minute overall wall cap). It does **not** claim to eliminate the underlying Gradle stall.
- The two Android lanes upload verified *actual* debug APK files; receipt uploads always preserve failed evidence. Every passing lane must also expose fresh parseable JUnit XML; Android lanes must retain lint reports. Desktop/Web publish native receipts and verified JUnit metadata. No release signing.
- One aggregated `Devtool CI gate` check fails if any source or matrix lane fails. Existing StudyFlow CI remains the authoritative required check until hosted Devtool acceptance.
- This patch **does not** modify `.devtool.toml`, legacy CI, other SFDT06–18 workflows, branch protection, required checks, signing keys, device acceptance or release workflows.

## Accept and switch

1. Apply the source-pinned overlay with the included Zsh installer. On the PR, require `Devtool CI gate` to pass alongside existing `StudyFlow CI` and `SFDT11` source-policy checks. Confirm the two actual APK artifacts and all four receipts are downloadable. Check the Android phone lane for regressions in the previous post-lint Gradle silence.
2. Merge the PR only after hosted jobs pass. Confirm the new `main` push runs `Devtool CI gate` green once more.
3. **Then** switch the repository's required check to `Devtool CI gate` and retire/disable the duplicate automatic legacy CI jobs in a *separate, small activation change*. Keep an explicit manual legacy rollback route. Do not silently change branch protection in this PR.
4. Release signing and device acceptance remain independently protected. PR/push CI success **does not** qualify a signed release.

This retains a straightforward rollback: before activation, stop using the candidate workflow; after activation, restore the original `studyflow-ci.yml` from the retained commit and reset required checks.

The old five-lane SFDT16/17/18 manual evidence coordinator is no longer part of the migration acceptance path. Its historical files are retained for audit rather than aggressively deleted in this change.

## Source-only validation

```sh
python3 -B tests/test_studyflow_devtool_primary.py
python3 -B scripts/sfdt07_consolidated_contract.py --check
python3 -B scripts/sfdt08_contract.py --check
python3 -B scripts/sfdt11_contract.py --source-check
```
