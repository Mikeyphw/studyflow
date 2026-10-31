# OV01-R4R5 — hosted CI remediation and promise-by-promise audit

**Base:** `ed99860d8a58d2c2dd74259e1e93e1782d08bcfb` (R4R4). Failed standard CI `41142660960` and deep run `41142677114`.

| Promise | Implementation | Evidence and boundary |
|---|---|---|
| Unsigned phone Release unit tests do not require production signing | Exclude `packageRelease*UnitTest*` Gradle preparation from the `verifyPhoneReleaseSigningPreflight` dependency; do not exempt real app output names | Static source and mutation tests; hosted graph **pending** |
| Signed APK/AAB still fails closed | Keep `assembleRelease`, `bundleRelease`, actual `packageRelease*`, `signRelease*` dependencies and gate implementation | Source assertions; production signing qualification **not run** |
| DOV04/05 bootstrap task in Devtool | Add exactly one explicit `verifyStudyFlowDesktopBootstrapScreens` to desktop test and unit-test plans | Run bootstrap static verifier; hosted desktop **pending** |
| Quality source roots reflect real owner | Check parsed TOML quality input `desktopApp/**`, Python formatter `ROOTS`, and registered root Gradle formatter | Source-owner checks; do not add unused literals |
| REL-FINAL audit covers every current verifier without falsifying legacy counts | Frozen 213 legacy + 16 existing post-REL-FINAL + explicit 54 additions. Require exact full-set equality, disjoint categories, and count | Actual files enumerated and checked; hosted deep parity **pending** |
| Preserve evidence boundaries | No changes to native release terminal seal, signing material, real-device gates, or release readiness claims | `qualified_release=false`, actual release remains pending |

No local Gradle. All static tests and disposable transaction reports document source readiness only. R4R5 should be accepted only after standard and all 5 deep hosted jobs pass on the same pushed source SHA.
