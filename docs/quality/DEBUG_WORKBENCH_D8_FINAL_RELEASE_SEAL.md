# Debug Workbench D8 Final Source Seal

Acceptance criteria for the Debug Workbench source/diagnostic chain; this document does not certify the signed Release artifact or real-device acceptance:

- D1 through D7 milestones remain present.
- `DebugWorkbenchScreen` exposes self-check history and support-bundle seal state through explicit parameters.
- `verifyRepositoryContracts` and `scripts/validate_repo.sh` run all historical Phase 0 through Phase 29 validators and every Debug Workbench validator through D8.
- Final support export remains allowlist-only and excludes databases, DataStore, SharedPreferences, annex payloads, study text, Logcat, and automatic upload paths.
- English and Brazilian Portuguese resources remain key-aligned.

## D8 r3 hotfix

D8 r3 fixes two inherited JUnit `@Test(expected = ...)` expression-body tests by making them explicit block-body `Unit` methods. It also hardens the D1 diagnostics verifier so generated Gradle `build/` outputs are skipped while source-side cache and bytecode guards remain active.



## D8 r4 hotfix

D8 r4 keeps the r2/r3 final-seal fixes and normalizes coroutine debug thread-name suffixes in workbench action tests so Gradle runs with kotlinx coroutine debug naming do not fail on ` @coroutine#...` suffixes.


### D8 r5 lint-floor hotfix

- Routes notification channel importance reads through an API-guarded helper so minSdk 23 lint does not flag `NotificationChannel.getImportance`.
- Replaces `java.util.Base64` mapper use with `android.util.Base64` and `Base64.NO_WRAP` to preserve encoding while supporting the app minSdk.
- Extends the D8 verifier to reject both regressions.

D8 r6 lint-floor hotfix keeps every prior D8 fix and replaces Android UI java.time calls with minSdk-23-safe java.text/java.util date helpers in progress, schedule, and sessions screens.
