# StudyFlow OV01-R4 — source-bound GitHub CI remediation

**Base:** public GitHub commit `d1dd6e27dfe3a1e0210de350ec1f0ef5c307e45e` (OV01-R3R2). **GitHub-only Gradle:** mandatory; no Gradle tasks executed during preparation.

## Failing evidence

| Lane | Run | Evidence | Remedy |
| --- | --- | --- | --- |
| Deep Android | 41123923322 | `:app:verifyPhoneReleaseSigningPreflight` lacks four release credentials | Unsigned profiles use Debug unit tests, preserving Release preflight |
| Standard Android | 41123770397 | Same signing preflight failure | Standard Android matrix Debug unit tests |
| Standard Web + deep parity | Both | Historical OV30 verifier expects `src/**`, `tests/**`, `package.json` although Devtool input paths are repository-relative | Correct verifier; keep `web/*` config |
| Deep Desktop | 41123923322 | DOV08/DOV09 verifier cannot find exact task registrations in Devtool config | Register tasks in desktop test profile |
| Deep Quality | 41123923322 | 5 historical archived phase owners asserted in active aggregate, legacy root manifest identity, 3 missing explicit ignores | Verify active vs archived owners separately, recognize root manifest provenance, restore ignore rules |
| Deep TV | 41123923322 | Passed | Not modified |

## Promise-by-promise status

- **P0 unsigned Android jobs no longer select Release test tasks:** implemented, source tested; hosted pending.
- **P0 Release signing remains fail-closed:** code retained and source tested; signed release pending.
- **P0 legacy web validation aligns with real Devtool inputs:** implemented, source verified; hosted pending.
- **P1 DOV08/DOV09 registered within Devtool test target:** implemented, source verified; hosted pending.
- **P1 active / archived source contracts differentiated:** implemented, source verified; hosted pending.
- **P1 root overlay provenance not overwritten:** implemented, source verified; hosted pending.
- **P1 transient Devtool files explicitly ignored:** implemented, source verified; hosted pending.
- **P2 regression gate run before hosted Gradle:** implemented, source tested; hosted pending.
- **Native Devtool EXO Gradle receipts, real release signing, Android-device tests:** explicitly out of scope; open.

## Execute acceptance

1. Apply the ZIP with `devtool --repo "$PWD" --yes apply-overlay "$ZIP" --static-validate --no-commit --keep-artifact` on the exact committed base.
2. Run `./devtoolw ci-source` and `python3 scripts/test_studyflow_ov01_r4_contracts.py` — source-only, no Gradle.
3. Commit/push then monitor **both** the standard push CI and separately dispatch `lane=deep` pinned to the resulting HEAD SHA.
4. Inspect job logs and machine-readable `studyflow-devtool-*-<runid>-<attempt>` receipts, do not claim a release or acceptance from source tests alone.

## R4R2 supplemental promise audit — corrections superseding original R4

1. **Devtool web path semantics:** original R4 was incorrect. Devtool resolves `inputs` and `outputs` against `root=web`. With `web/` prefixes, files were misidentified as `web/web/...`. R4R2 fixes the real configuration and verifies target-relative paths, not only the legacy OV30 assertion.
2. **Signing-sensitive transitive graph:** original R4 replaced the directly selected Release unit test, but retained root Gradle verifier edges to `:app:testReleaseUnitTest`. The app's broad `packageRelease*` gate accidentally captured Gradle's `packageReleaseResources` used by those tests. R4R2 retains the broad production `packageRelease*` signing gate except the exact AGP resource-preparation task `packageReleaseResources`; real package/assemble/bundle/sign outputs remain protected. This is a source-only hypothesis pending hosted verification. Confirm on hosted CI before acceptance.
3. **Native desktop phases:** R4 added DOV08/DOV09 to `tasks.test` but not to `validation.phases.unit-test`. R4R2 adds the missing phased execution registrations.
4. **Historical root overlay:** preserve and pin the currently reviewed historical OV02 manifest name; arbitrary name substitutions must fail.

**R4R2 is a complete replacement for R4 and targets the same committed base. Do not apply R4 first.** No local Gradle task execution. R4R2 acceptance on GitHub is still pending.
