# StudyFlow ↔ Devtool: GitHub-only Gradle adoption (2026-10-30)

**Policy:** Gradle builds, tests, and lint belong on GitHub Actions. Default Gradle settings in the root project and included build now refuse local execution unless the GitHub Actions Linux CI environment markers are present. Gradle builds/tests/lint run exclusively in GitHub Actions. Read-only Python static contracts and Devtool EXO source-only jobs remain local. This is a workflow policy, not a sandbox: directly calling `devtool build`, `gradlew`, or a Gradle target can still execute locally and must not be done.

## New deployment split

- `./devtoolw` and `./devtoolw ci-source` run only the native EXO DAG of three Python source contracts: CI file presence, GitHub workflow/source consistency, and GitHub-only Gradle task-profile policy. Legacy local Gradle wrapper aliases (`build`, `tv`, `desktop`, `test`, `validate`, `ci-local`, `ci-workspace`) are no longer exposed. The canonical Gradle targets in `.devtool.toml` are **retained** as metadata and for source-compatible task planning; they are not a local CI entry point.
- `studyflow-ci.yml` still provides the fast push/PR build/test/lint jobs, plus a manually dispatchable `lane=deep`. Deep launches five independent matrix workers, each of which invokes the **exact canonical configured target task list** from `.devtool.toml`: `studyflow_android#test`, `studyflow_tv#test`, `studyflow_desktop#test`, `studyflow_quality#build`, and `studyflow_parity#build`.
- `scripts/studyflow_ci_gradle_profile.py` is the source-bound bridge. `--plan` and `--check-source` never call Gradle. `--run` requires GitHub Actions CI/Linux environment and an exact local HEAD / `GITHUB_SHA` match; it invokes `gradlew` without shell expansion and writes a receipt even if Gradle exits nonzero. The receipt includes target, operation, exact hosted Gradle argv, tasks, target config SHA-256, Git SHA, attempt, outcome, **`native_exo_gradle_execution: false`**, and **`qualified_release: false`**. It is *a project-owned task profile receipt*, not an EXO runtime receipt.
- Manual debug APK/AAB and all manual `studyflow-ci.yml` dispatches **require** `expected_sha`, validated against the GitHub runner's source SHA. A missing or invalid SHA fails. Moving branches cannot silently build different source, but the check is not cryptographic attestation or a release qualification.
- `settings.gradle.kts` and `build-logic/settings.gradle.kts` refuse Gradle configuration when GitHub Actions/Linux CI environment markers are absent. These are practical policy guards, **not** tamper-proof sandboxing: a caller may spoof environment variables or use an alternate settings script. Use source-only local Devtool operations.

## Read-only local commands (Termux)

```sh
cd "$HOME/Code/studyflow"
./devtoolw ci-source
python3 scripts/studyflow_ci_gradle_profile.py --check-source
python3 scripts/test_studyflow_ci_gradle_profile.py  # fake runner only; never Gradle
python3 scripts/studyflow_ci_gradle_profile.py --plan android-contracts
python3 scripts/studyflow_ci_gradle_profile.py --plan quality-contracts

devtool --repo "$PWD" workflows --json
devtool --repo "$PWD" preflight --workflow ci-source --target studyflow_android --json
devtool --repo "$PWD" actions diagnose --json
devtool --repo "$PWD" actions workflows --json
```

The source-only EXO DAG works with the provided native Termux Devtool; on non-Termux Linux hosts, use `--execution-environment host` for `preflight`/`invoke`. No Gradle calls are needed to check the integration.

## Github Actions through Devtool: full deep contract lane

Commit and push the **applied source** before dispatching. A workflow must be registered on the default branch to be discoverable for manual dispatch. Use a reviewed branch and the exact SHA of its pushed HEAD:

```sh
cd "$HOME/Code/studyflow"
REF="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
git status --short

devtool -r "$PWD" actions plan studyflow-ci.yml --ref "$REF" \
  --input lane=deep --input expected_sha="$SHA" --json

devtool -r "$PWD" actions run studyflow-ci.yml --ref "$REF" \
  --input lane=deep --input expected_sha="$SHA" --yes --json
```

Never infer the GitHub run ID from recency. Retain the Devtool dispatch `request_id`; if GitHub accepted the request without a safely bound `run_id`, use `actions resume --request-id` and read the correlated result. For a correlated run:

```sh
devtool -r "$PWD" actions view --run-id RUN_ID --json
devtool -r "$PWD" actions jobs --run-id RUN_ID --json
devtool -r "$PWD" actions watch --run-id RUN_ID --interval 10 --max-checks 120 --json
devtool -r "$PWD" actions artifacts --run-id RUN_ID --json
```

Every deep matrix worker attempts to upload its `studyflow-devtool-<profile>-<run>-<attempt>` receipt on completion/failure. A missing receipt must be treated as missing evidence, **not** as a successful task. A receipt that says `passed` still requires a successful GitHub job/run, exact SHA checks, and artifact verification before it can inform acceptance. GitHub Actions logs and `actions download` allow investigation; Devtool `actions logs` saves a raw log ZIP without extraction.

## Debug package: phone or TV

```sh
devtool -r "$PWD" actions plan devtool-android-build.yml --ref "$REF" \
  --input expected_sha="$SHA" --input project_dir=. \
  --input module=app --input variant=debug --input format=apk --json

devtool -r "$PWD" actions run devtool-android-build.yml --ref "$REF" \
  --input expected_sha="$SHA" --input project_dir=. \
  --input module=app --input variant=debug --input format=apk --yes --json
```

Change `module=app-tv` for TV or `format=aab` for a debug bundle. After GitHub has finished successfully, use Devtool `actions android collect --run-id RUN_ID --artifact-id ARTIFACT_ID --format apk --variant debug --expected-package com.mikeyphw.studyflow --download --json` (`com.mikeyphw.studyflow.tv` for TV). It uses Android package inspection tools locally **without running Gradle**, and yields an unqualified verified debug artifact. Exact `artifact-id` is obtained from `actions artifacts` for the selected run. No secret-backed release signing is introduced in this overlay.

## Boundaries

1. **Native Devtool local Gradle entry points still exist but ordinary Gradle configuration now fails locally.** Both root and included-build settings check GitHub Actions/Linux CI environment markers. This is not an identity/security boundary or enforcement across custom `--settings-file` invocation; do not call local `devtool build/test/validate`, `gradlew`, or release scripts that invoke Gradle.
2. **The hosted deep lane is opt-in**, is isolated from branch-push CI cancellation, and uploads mandatory (not silently absent) JSON receipts. It is not a mandatory branch protection check and has not yet been run against the live GitHub repository. Existing fast push/PR checks remain; `lane=all` means the original fast client matrix, not the deep lane.
3. **GitHub Actions is the Gradle execution host; Devtool remains the orchestration/inspection operator.** The project bridge consumes canonical Devtool *task lists* but deliberately overrides hosted Gradle flags (`--no-daemon --max-workers=2`); it does not invoke the Devtool native Gradle runner, preserve every Devtool runner knob, or emit native EXO Gradle evidence.
4. **Static Android discovery currently fails** with `Dynamic Gradle include requires explicit reviewed override`: StudyFlow's `settings.gradle.kts` has a trailing-comma include declaration and its application ID is delivered via a compiled Kotlin convention plugin in `build-logic/convention`, not the static discovery source layout. Do not invoke `--discovery-mode gradle` locally (that would execute Gradle). Use generic `actions plan/run` until Devtool's static parser is extended and tested.
5. A GitHub-hosted runner is not a physical Android/TV device. Release signing, instrumentation, on-device recovery, and product acceptance remain separate evidence gates. The personal-signing Gradle model is left unchanged.

## R2 audit gate (source-only)

The replacement OV01-R2 adds two guard settings scripts, mandatory commit pinning on manual dispatch, isolated deep CI concurrency, fail-closed receipt upload, an exact hosted Gradle argv field, and negative tests with a harmless fake executable. Its overlay validation executes only Python/static Devtool checks. The `--run` fake-hosted test does **not** execute a real Gradle binary. See [OV01 hard-audit evidence](STUDYFLOW_DEVTOOL_OV01_HARD_AUDIT.md).

**Release constraint:** Existing Devtool personal-release Gradle targets are still configured for local Termux, but must **not** be executed under the GitHub-only policy. OV01-R2 does not introduce hosted release signing or migrate the existing keystore. Release Gradle tasks remain blocked until a separate secure hosted-release design is implemented. Non-Gradle signing of a verified hosted-built package is a separate question that requires certificate and provenance controls.
