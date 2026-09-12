# StudyFlow Gradle / Devtool Task Graph Optimization V4

This pass preserves the existing verification contracts while removing repeated Gradle launches and repeated heavy Web/native work from routine validation.

## Why this exists

The pre-v4 Devtool configuration used `split_gradle = true` for Android, Desktop, TV, and parity. The durable validation phases then repeated tasks that later assemble/test/lint work already implied. TV was the worst case: 196 phase task placements across five validation phases, with the same source verifiers repeated in preflight, compile, unit-test, package, and lint.

The failed ATT roadmap v3 Termux run also proved that static source gates are not a substitute for native compilation: `StudyFlowTvAttentionRuntimeTest` still used the old constructor and failed `:app-tv:compileDebugUnitTestKotlin`. V4 fixes that compile break and keeps the TV unit suite in the terminal Attention seal.

## Devtool configuration audit and fixes

- Migrated legacy `[project].kind` / `[project].default_target` and target `type` keys to canonical `runner`, `platform`, and `[workspace].default_target`.
- Set the default target to `studyflow_android`.
- Added workspace groups for clients, mobile clients, release targets, and the complete workspace.
- Added the safe workspace scheduler with `max_gradle_nodes = 1`, so parallel workflows cannot run multiple Gradle heaps concurrently.
- Added a separate `studyflow_tv_release` target. Routine `studyflow_tv` no longer builds optimized Release APKs or packaged-release evidence during ordinary debug/test/lint validation.
- Migrated Android/TV device configuration to nested `android.adb` and `android.instrumentation` tables, using `first-physical` selection and `when-device-present` instrumentation.
- Set Android/Desktop/TV/parity validation to `split_gradle = false`.
- Explicitly disabled redundant `dependencies`, `compile`, and `resources` durable phases where assemble/test/lint already own those dependencies.
- Removed contradictory `parallel = false` versus `-Dorg.gradle.parallel=true` policy. Gradle and validation now use one explicit parallel policy.
- Matched the repository validation defaults to the Termux execution profile: 4 workers / 4 CPUs, 2048 MiB Gradle heap, 768 MiB metaspace, memory guard 0.
- Disabled `affected_module_lint` where StudyFlow deliberately uses app-level dependency lint. Android keeps Debug+Release lint in its main lane; routine TV runs Debug lint while `studyflow_tv_release` owns Release lint. Dependency lint is enabled through `lint.checkDependencies = true`.

## Task graph consolidation

- Added `verifyCpovAllContracts`, `verifyStudyFlowDesktopAllContracts`, `verifyStudyFlowTvAllContracts`, and `verifyStudyFlowAndroidPreflightContracts` aggregates.
- Consolidated terminal Attention validation around `verifyAttentionAllContracts` + one complete Web regression/release gate + native suites.
- Consolidated WEB20, Presence, ATT12, and post-seal Web validation onto shared runtime-parity, pytest, release-validation, and release-integrity tasks.
- `verifyStudyFlowWebFullRegression` reuses the already-verified runtime parity through `STUDYFLOW_EXTERNAL_RUNTIME_PARITY=1`.
- Removed the redundant explicit `tsc --noEmit` before `compile_runtime.mjs`; the runtime compiler remains the single TypeScript compilation/parity authority.
- Converted duplicate Desktop subproject verifier `Exec` tasks into compatibility aliases of the canonical root tasks.
- Removed unconditional lint-report deletion from repository contracts so Gradle/Android lint can keep its incremental state.
- `verifyStudyFlowGradleTaskGraphOptimizationV4` is a static topology/config gate only; it no longer depends on Android/Desktop/TV preflight aggregates, so selecting the terminal Attention seal does not accidentally execute the whole repository validation graph.
- Added `verifySflp18Sflp19NotificationMediaContracts` as a focused source-contract gate. The historical `verifySflp18Sflp19NotificationMediaSync` still owns its predecessor/native chain, while ATT12 consumes the focused contract plus its own native suites and avoids recursively replaying SFLP01-SFLP17.
- Android lint uses app-level Debug/Release lint and TV lint is split between routine Debug and release-only Release lint; `checkDependencies = true` covers dependent Android modules without enumerating ~40 module lint tasks.
- Repository-wide `verifyFormatting` is owned only by `studyflow_quality`. Phase 15 now checks ownership of that canonical gate instead of duplicating the entire formatting scan in Python.

## Phase-placement reduction

Compared with the uploaded baseline configuration:

| Target | Baseline validation placements | V4 placements | Reduction |
| --- | ---: | ---: | ---: |
| Android | 123 | 37 | 86 |
| Desktop | 47 | 9 | 38 |
| TV | 196 | 6 | 190 |
| Parity | 26 | 2 | 24 |

Routine TV source verifiers remain individually addressable for diagnosis, but one aggregate gate owns them in validation preflight instead of repeating them in every phase. Release-only TV work is isolated in `studyflow_tv_release`, which has 8 phase placements and owns Release lint/artifact/device acceptance.

## Validation contract

`verifyStudyFlowGradleTaskGraphOptimizationV4` fails if the old expensive topology returns. It also checks:

- canonical Devtool schema and workspace configuration;
- coalesced validation and disabled redundant phases;
- terminal ATT/SFLP task ownership;
- app-level lint aggregation;
- routine-vs-release TV target isolation;
- shared Web gate reuse;
- Desktop aliasing;
- the ATT v3 `localPeer` compile regression;
- the always-true Kotlin conditions reported by the real Termux compiler.

The optimization does **not** remove the focused historical Gradle tasks. They remain callable for debugging a specific roadmap phase; they are simply no longer stacked into the default terminal path when a stronger aggregate seal already owns them.
