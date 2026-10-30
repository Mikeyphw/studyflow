# Devtool orchestration in StudyFlow

Effective from the GitHub-only Gradle adoption overlay of 2026-10-30. Read the detailed [GitHub-only Gradle guide](DEVTOOL_GITHUB_ONLY_GRADLE_20261030.md) and [cumulative improvement ledger](DEVTOOL_IMPROVEMENT_LEDGER.md).

- **Local Termux:** `./devtoolw ci-source` (also the wrapper default) invokes three read-only source contracts through native Devtool target-local EXO jobs and an explicit MP03 dependency DAG. `devtool --repo "$PWD" workflows --json` and `preflight --workflow ci-source --target studyflow_android --json` are read-only. `python3 scripts/studyflow_ci_gradle_profile.py --plan android-contracts` prints the canonical Gradle task plan without invoking Gradle.
- **GitHub Actions:** `devtool actions plan/run studyflow-ci.yml --ref BRANCH --input lane=deep --input expected_sha=FULL_SHA --json` is the deep Gradle route. Existing push/PR Android, TV, desktop and web CI remains fast; the deep lane is opt-in.
- **Debug packages:** `devtool actions plan/run devtool-android-build.yml --ref BRANCH --input expected_sha=FULL_SHA --input project_dir=. --input module=app --input variant=debug --input format=apk --json`. After the run, use `actions view/watch/artifacts/android collect` to reconcile the exact run, SHA, package and signing evidence.

The local wrapper intentionally does not provide `build`, `test`, `validate`, `tv`, `desktop`, `ci-local`, or `ci-workspace` aliases any more. Direct `devtool build` or a direct Gradle command remains addressable, but ordinary configuration is refused outside GitHub Actions by root and included-build settings guards. This is environment-based accidental-execution protection, not a security sandbox. Do not attempt local Gradle through alternate settings or spoofed environment markers. The retained `.devtool.toml` native Gradle targets are canonical metadata for the hosted task bridge; changing their execution environment to an unsupported GitHub mode is neither necessary nor safe.

GitHub build/task receipts are **not** release attestation or native EXO Gradle runtime evidence. Generic `actions plan/run` is used for StudyFlow because direct static Android discovery currently does not support its Gradle project layout. Never use `--discovery-mode gradle` locally just to bypass this limitation.

Manual GitHub Actions dispatches require `expected_sha`; deep jobs use a distinct concurrency identity from push CI and fail the receipt upload on missing evidence. Source-only negative tests use a fake stub rather than Gradle. See [hard audit](STUDYFLOW_DEVTOOL_OV01_HARD_AUDIT.md).
