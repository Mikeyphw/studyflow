# StudyFlow OV01-R4R7 — hosted failure audit and promised fixes

Base GitHub commit: `15ecd0c5d57ad5e7edbe5405bf440259675bae53` (R4R6). Host evidence: standard `41158049943`, deep `41158051397`. Local work is source-only. Gradle must run on GitHub Actions.

## Promise-to-evidence matrix

| Scope | Host evidence | Delivered R4R7 source change | Source verification | Hosted acceptance |
| --- | --- | --- | --- | --- |
| Android manifest security | `ManifestSecuritySourceContractTest:25` rejects Termux `<queries>` | Strict XML DOM allowlist for exactly one `com.termux` package query and one `com.termux.RUN_COMMAND` intent; Phase 18 checks the same boundary; backup/FileProvider assertions unchanged | Structural verifier, deliberate broad-query rejection test | Pending GitHub JVM test |
| Widget deadline | `StudyFlowWidgetSnapshotProjectorAtt03Test:125` drops a boundary 30s ahead | Filter strictly future domain refresh boundaries, retain periodic minute tick and separate scheduler 1s immediate-wakeup floor; add 15s review regression | Kotlin source contract and static mutation tests | Pending GitHub JVM test |
| OV15 validation-file error | Deep Android `FileNotFoundError` for `.devtool-artifact/validation/validate.sh` | Check tracked OV15 runner and Gradle ownership; inspect transient hook only if present | OV15 source verifier and mutation test | Pending GitHub deep Android |
| Adjacent OV13/OV17 source preconditions | Same ephemeral-hook assumption reachable later in Android task graph | Track canonical OV13/OV17 script runners; inspect optional present hook for daemon-stop prohibition | OV13 and OV17 source verifier | Pending GitHub deep Android |
| Phase 18 package visibility | Historical prohibition contradicts Termux bridge | Keep implicit ACTION_VIEW contract while allowing only audited Termux query | Phase 18 source verifier, manifest mutation rejection | Pending end-to-end CI |
| Desktop SFLP02/03 line limits | 24 lines in `build.gradle.kts` over 120 chars, first line 11, last line 2748 | Split descriptions into Kotlin `+` concatenation and 8-arg `dependsOn` into multiline; preserve all original description values and ordered Gradle dependency names | Strict SFLP verifier, descriptor digest and task-name equality | Pending GitHub deep desktop |
| GitHub regression gate | R4R6 regressions already passed in source job | Add new R4R7 contracts to source job; update cumulative ledger SF-DT-39–42 | New source regression and Devtool required validation hook | Pending hosted source job |
| Release safety | Signing preflight remained mandatory for actual Release APK/AAB packaging | No changes to `app/build.gradle.kts`, release packaging tasks or release qualification | Existing source signing and task-graph verifiers | Signed release/physical device OPEN |

## Historical and remaining boundaries

- Deep R4R6 parity, quality, TV and source contracts were already green. Standard web, desktop, TV and source jobs were green. R4R7 changes should preserve all of those.
- Source-only acceptance does not prove Kotlin compilation, unit tests, native Gradle graph, released APK/AAB signatures, physical-device behavior or Devtool EXO receipts.
- The schema-v2 ZIP binds all existing changes by SHA256 and validates its final output in a disposable tree. It must reject stale/mismatched GitHub baselines before mutating the repository.
