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
