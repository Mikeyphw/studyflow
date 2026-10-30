# StudyFlow / Devtool OV01 — adversarial hard audit and replacement R2

Date: 2026-10-30. Original snapshots: StudyFlow `8997be26d2f60f214b64b467a4c633d22d0b4d21`, Devtool snapshot `8a356a9` (from the supplied archives). Original candidate `STUDYFLOW_DEVTOOL_GITHUB_ONLY_GRADLE_OV01_20261030.overlay.zip` is superseded by the **one-shot replacement OV01-R2**, which is source-bound to the **same original** StudyFlow snapshot. Never apply OV01 and R2 sequentially.

## Findings and corrections

| Claim / requirement | OV01 observed behavior | Impact | R2 behavior | Evidence status |
| --- | --- | --- | --- | --- |
| Gradle exclusively on GitHub | Local wrapper aliases removed, but native Gradle target and `gradlew` could still configure locally | P1 accidental local execution | Root + included-build Gradle settings reject non-GitHub Linux CI environment markers; wrapper defaults remain source-only | Source-level PASS; live Gradle settings evaluation **not tested** |
| Manual dispatch source-bound | `expected_sha` was optional | P1 reviewed source could be omitted | Required exact 40-lowercase-hex SHA for all manual CI and debug builds | YAML/static PASS; live dispatch open |
| Long-running deep CI preserves review run | `concurrency.group` based only on branch/ref; push may cancel deep run | P2 interrupted evidence | Manual dispatch group includes run ID and event type | Expression inspected; live scheduler open |
| Native Devtool hosted task execution | Python script consumed `.devtool.toml` tasks, executed `gradlew` directly | P1 feature overclaim if called native EXO | **No false claim**: receipt explicitly records `native_exo_gradle_execution=false`, target task plan and exact hosted invocation | Static/negative PASS; native EXO hosted still open |
| Task-profile portability | Task list read from config, but Devtool runner tuning not preserved | P2 semantic parity gap | Explicit documented hosted `--no-daemon --max-workers=2`; records exact argv | Static PASS; effectiveness unverified |
| Artifact receipt guarantee | `if: always()` but `if-no-files-found: warn` | P2 missing evidence could be overlooked | Set `if-no-files-found: error`; test nonzero stub and receipt fields | Stub PASS; real runner open |
| Gradle task existence | No declaration inventory check | P2 possible missing root task | Inspected **65** non-module tasks across five profiles; all declared in `build.gradle.kts` | Source-level PASS; plugin-generated module tasks not resolvable without hosted Gradle |
| Negative cases | Only success-oriented source-string checks | P2 fragile confidence | Local refusal, malicious task, SHA mismatch, failed-receipt tests with harmless fake script | PASS; no Gradle executed |
| Release Gradle through GitHub | Existing personal Termux release Gradle targets retained; docs advised local signing | P1 violates stated execution policy if used | Docs mark old local Gradle release path **blocked**; no signing secrets moved | Policy explicit; hosted release migration open |
| Overlay source integrity | Touched-file hashes vs original snapshot | Safe conflict boundary | Retain atomic, fail-closed source contract; add source guards/tests/docs | ZIP check and application acceptance required |

## Specific remaining limitations

1. `settings.gradle.kts` gates prevent ordinary accidental local configuration, but environment checks are **not identity authentication**. A caller can impersonate CI variables or provide another settings script. Upstream Devtool needs a native target execution authority field to prevent direct `devtool build/test/validate` from selecting a local Gradle backend.
2. The GitHub Python bridge is **not** the Devtool native EXO Gradle runner and does not preserve every runner policy flag, validation phase, multi-target scheduler, or EXO receipt. It uses canonical configured task lists. A native Devtool provider-export/pipeline design is a separate integration enhancement requiring review and a tested hosted environment.
3. GitHub hosted Gradle has **not** run. No Android compilation, TV/desktop verification, real artifact, cert acceptance, release signing, or physical-device behavior can be claimed.
4. Only deep `workflow_dispatch` uses the five target profiles. The normal push/PR jobs retain their fixed task sets; `lane=all` still means ordinary fast client CI, not deep.
5. The release targets still declare `execution_environment="native-termux"` and keystore locations. They are preserved as existing metadata, but **must not be executed** under the GitHub-only Gradle policy. Hosted release packaging and secure signed-release chain remain open.
6. A job failing before Python starts may have no receipt even with `if: always()`. `if-no-files-found: error` makes this failure observable, but the GitHub job conclusion remains the authority.
7. Full actionlint, hosted Gradle settings compilation, hosted task output, performance/runner minutes, Gradle module/convention-plugin discovery, and release/device gates still require GitHub-hosted verification.

## Offline audit performed

- Compared the original overlay files, manifest hashes, and validation hook against the original StudyFlow and Devtool repositories.
- Confirmed baseline touched SHA-256 values match all six original files, and the source-bound candidate applies to a disposable original copy with `--static-validate` and no Gradle.
- Parsed Actions YAML and confirmed triggers, matrix, required SHA fields, and job definitions.
- Parsed `.devtool.toml` and enumerated each of five deep profiles: Android test (57 tasks), TV test (4), desktop test (9), quality build (20), parity build (8). All 65 non-module Gradle task names were found as top-level declarations; generated `:module:*` tasks remain unproven.
- Executed project static contract scripts and adversarial fake-executable test. The test never starts a Gradle binary.
- Validated Devtool CLI syntax for `actions plan/run`, `overlay check`, `apply-overlay --static-validate`, and `actions android collect` against supplied Devtool source.

## Post-application live gates

1. `./devtoolw ci-source` must pass locally (read-only). Do **not** invoke Gradle locally for acceptance.
2. After committing and pushing, dispatch the exact SHA-pinned deep run and inspect each of five matrix receipts plus the GitHub job conclusion.
3. Confirm root/included-build settings evaluate correctly **via GitHub-hosted Gradle only** and all module-generated Gradle tasks exist.
4. Retrieve the original GitHub run and artifact IDs with Devtool; require matching SHA/attempt and verify Android signatures/package identity before acceptance.
5. Close hosted release signing and physical-device testing separately; do not equate debug builds with release qualification.

## Decisions

R2 is safer than OV01 but its acceptance is **source-only**. The original OV01 should not be applied. Replace it with R2 *once* and verify hosted CI; no Gradle commands run locally in the audit.
