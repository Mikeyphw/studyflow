# StudyFlow ↔ Devtool cumulative improvement ledger

Last examined: 2026-10-30. Source: StudyFlow `8997be26`; Devtool `8a356a9` in provided archive snapshots. Keep this document cumulative and append future changes/evidence rather than replacing earlier findings. PASS denotes static/source-only proof unless explicitly stated otherwise.

| ID | Priority | Finding and evidence | Affected repository | Change/status | Remaining gate |
| --- | --- | --- | --- | --- | --- |
| SF-DT-01 | P1 | Local `./devtoolw build`, `test`, `tv`, `desktop`, and `validate` aliases called Gradle despite hosted-only Gradle policy. | StudyFlow `.devtool.toml` | **R2 patched**: remove local Gradle wrapper aliases and add fail-by-default settings guards in root and included-build | Environment spoofing/alternate settings remains possible. Future Devtool execution-authority is a stronger boundary |
| SF-DT-02 | P1 | `studyflow-ci.yml` exercised only selected Android/TV, desktop, web tasks, not the target-local quality/parity/full test task lists. | StudyFlow Actions | **Patched**: opt-in five-cell `deep` matrix using configured Gradle target profiles | Real GitHub workflow execution, all matrix receipts, and correct runner dependencies |
| SF-DT-03 | P1 | Target task lists were duplicated across Devtool and CI and could drift. | StudyFlow | **Patched**: read task arrays directly from `.devtool.toml` under strict target/operation allowlist; source-only planning | Confirm all deep tasks are executable with hosted toolchains |
| SF-DT-04 | P1 | Workflow dispatch did not expose a reviewed source SHA gate to human/automated callers. | StudyFlow Actions | **R2 patched**: required `expected_sha` for every manual CI/debug dispatch; SHA checked against GitHub checkout | A Devtool provider dispatch can still be accepted just as branch HEAD moves; use source-bound input and reconcile actual run `head_sha` |
| SF-DT-05 | P1 | Deep CI had no per-target portable machine receipt for selected tasks, config identity, and result. | StudyFlow Actions | **R2 patched**: JSON task receipts contain the hosted argv; `if: always()` upload uses fail-if-missing | Live-run artifact evidence and independent result/status reconciliation; these are not native EXO receipts |
| SF-DT-06 | P2 | Native source-only EXO job graph did not enforce GitHub-only Gradle policy. | StudyFlow | **Patched**: third `ci-github-gradle-policy` job after source contract | Verify on user Termux runtime; Linux source-only EXO verification done with explicit host environment |
| SF-DT-07 | P2 | Current Devtool static module parser rejects StudyFlow's multi-line Kotlin `include(...)` with trailing comma; it also only recognizes convention IDs through a fixed script path that is not StudyFlow's `build-logic/convention` binary plugin. Direct `discover_modules` raised `Dynamic Gradle include requires explicit reviewed override`. | Devtool `devtool/actions_android_model.py`, StudyFlow `settings.gradle.kts` | **Open upstream**: improve static module parsing and reviewable binary-convention-plugin mapping without evaluating Gradle | Add hostile dynamic-include negative fixtures and StudyFlow positive fixture; do not run local Gradle discovery |
| SF-DT-08 | P2 | Hosted GitHub Actions success does not qualify permanent personal-signing identity or on-device behavior. | StudyFlow release/device acceptance | **Open**: retain separate release/device authority; debug only | Design controlled signed-hosted release with protected secrets and stable certificate if needed; physical device evidence is separate |
| SF-DT-09 | P2 | Native EXO DAG scheduler is not the execution engine for hosted deep target profiles. | Devtool GitHub Actions architecture | **Partial**: use native EXO for local source DAG, Devtool Actions for remote dispatch/status, canonical task config for GitHub Gradle bridge | Future host-run Devtool EXO adoption needs pinned Devtool source, Ubuntu execution affinity and validated source-bound receipts |
| SF-DT-10 | P3 | Native Termux `execution_environment` makes local source-only DAG preflight incompatible on desktop Linux. | StudyFlow config | **Documented**: use `--execution-environment host` on non-Termux Linux; keep Termux execution identity intact | Evaluate per-job portable execution affinity without modifying existing contract verifiers |

## Acceptance evidence

- Source-only: `scripts/verify_studyflow_actions.py --source-only` PASS; `scripts/studyflow_ci_gradle_profile.py --check-source` PASS; `scripts/verify_studyflow_gradle_task_graph_optimization_v4.py` PASS.
- Native Devtool snapshot: `workflows --json` PASS; `preflight --workflow ci-source --execution-environment host` PASS; `invoke ci-source` PASS with **3 passed, 0 failed**, no Gradle tasks.
- **NOT DONE:** live GitHub dispatch/run, Gradle builds, packaging, signed release, physical-device acceptance, native Termux acceptance. Those results must never be inferred from offline checks.

## OV01 hard-audit addendum (R2, 2026-10-30)

| ID | Priority | Finding | R2 correction / evidence | Still open |
| --- | --- | --- | --- | --- |
| SF-DT-11 | P1 | OV01 only removed wrapper shortcuts, permitting accidental local `gradlew` configuration | Add guarded root/included Gradle settings after `pluginManagement` and negative source checks | Not an authentication boundary; direct Devtool runner has no native GitHub authority restriction |
| SF-DT-12 | P1 | Manual `expected_sha` was optional | Require and validate exactly 40 lowercase hex chars for every manual dispatch; source-only contract checks | No live GitHub dispatch acceptance yet |
| SF-DT-13 | P2 | A push on a reviewed branch would cancel its long-running deep job | Partition `concurrency.group` by event and use run ID for manual dispatch | Live concurrency confirmation |
| SF-DT-14 | P2 | Missing deep receipt was uploaded with `warn` | Set `if-no-files-found: error`; add `gradle_argv` and `native_exo_gradle_execution: false` to receipts | Jobs that fail before runner starts can still have no receipt (CI failure is authoritative) |
| SF-DT-15 | P2 | No adversarial tests covered source refusal, SHA mismatch, task command injection, and failure receipts | New source-only tests use a fake executable, never Gradle | Real hosted job acceptance |
| SF-DT-16 | P1 | Original OV01 docs retained obsolete four-file commit and local Gradle-release instructions | Refresh docs and block legacy local Gradle release until hosted route exists | Hosted secure release design |

**R2 evidence:** source-only Python contracts, negative fake-runner tests, YAML structure, source-bound overlay integrity, static Devtool validation; no actual Gradle or GitHub Actions build executed. Exact results are recorded in `STUDYFLOW_DEVTOOL_OV01_HARD_AUDIT.md`.

## GitHub Actions run 41119482183 — wrapper-validation incident (2026-10-30)

| ID | Priority | Evidence | Fix in OV01-R3 | Acceptance |
| --- | --- | --- | --- | --- |
| SF-DT-17 | P1 | All five `deep-contracts` jobs rejected `gradle/wrapper/gradle-wrapper.jar` in `gradle/actions/setup-gradle@v4`, before any Gradle execution. Original repository JAR was a custom 5,361-byte one-class bootstrap (SHA-256 `000a9d3e40bf33c4786ec759fc7dc79ca60f3a67b98902fd46e0f21c0464f250`), whereas Gradle's official 9.7.1 wrapper checksum is `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`. The Gradle distribution ZIP checksum already matches upstream. | R3 attempt failed when its network-dependent preflight followed a redirect to HTTP 404 (run `51a27a1e1aa949848a27982e2f0249c9`); Devtool verified rollback of six restored and one added paths. R3R2 instead requires staging the official JAR before the Devtool transaction, validates its published SHA-256, then installs it offline during the atomic preflight. Wrapper validation remains enabled. | Stage the wrapper before apply, verify `sha256sum` locally, commit binary, run hosted deep profiles again. No Gradle run locally. |
| SF-DT-18 | P2 | Five accompanying upload failures reported `if-no-files-found: error` because setup failed before `studyflow_ci_gradle_profile.py` could write a receipt. | Initialize `blocked_before_gradle` receipt immediately after checkout and upload it on failure; use `warn` for pathological failures before checkout, while explicitly requiring a completed successful receipt after a successful Gradle invocation. | Hosted setup-failure negative acceptance plus successful profile receipt. |

**Evidence boundary:** Root-cause analysis and source-only tests are complete. Downloading the official wrapper and CI build acceptance require the user's Termux/GitHub execution. The R3R2 Devtool transaction requires no network: stage a separately downloaded, SHA-256-verified official wrapper using the provided helper first. The overlay carries no unverified third-party binary. All Gradle tasks remain hosted.

### OV01-R3R2: failed R3 download remediation (2026-10-30)

- **Evidence:** R3 preflight returned HTTP 404 after redirect and Devtool restored all six modified files and the wrapper, removed the one newly added document, and verified the rollback (`51a27a1e1aa949848a27982e2f0249c9`). This failure was caused by a brittle distribution endpoint dependency inside the transaction, not a Gradle build failure.
- **Correction:** Use an offline after-extract preflight. User stages the official 9.7.1 wrapper before applying via a separate fetch script with independent download candidates and a hard-coded upstream checksum. The preflight verifies both the staged SHA-256 and the current repository wrapper identity; it never calls a network service or Gradle.
- **Acceptance:** Offline missing/invalid-staged-file rejection verified, source-only suite verified. **Success-path install of the authentic wrapper and hosted deep builds pending** until a verified binary is staged in Termux.
