# StudyFlow Debug Workbench D8 Final Release Seal

D8 closes the Debug Workbench chain after D1 through D7. It is a release-seal phase, not a new lab.

Implemented scope:

- fixes the D7 `DebugWorkbenchScreen` parameter wiring so self-check history and support-bundle seal state compile through the public composable boundary;
- adds a pure final-seal policy covering all Debug Workbench milestones, validators, privacy gates, and validation gates;
- adds behavior tests for the final-seal policy;
- upgrades the repository validator chain to run Phase 0 through Phase 29 plus D1 through D8;
- adds a D8 verifier that rejects missing wiring, missing validators, broad support export claims, Logcat capture, automatic upload, destructive probes, database export, and locale-resource drift.

D8 deliberately does not add Logcat capture, automatic upload, alarm enumeration, destructive probes, or Android Auto Backup execution.

## D8 r3 hotfix

D8 r3 fixes two inherited JUnit `@Test(expected = ...)` expression-body tests by making them explicit block-body `Unit` methods. It also hardens the D1 diagnostics verifier so generated Gradle `build/` outputs are skipped while source-side cache and bytecode guards remain active.



## D8 r4 hotfix

D8 r4 keeps the r2/r3 final-seal fixes and normalizes coroutine debug thread-name suffixes in workbench action tests so Gradle runs with kotlinx coroutine debug naming do not fail on ` @coroutine#...` suffixes.


### D8 r5 lint-floor hotfix

- Routes notification channel importance reads through an API-guarded helper so minSdk 23 lint does not flag `NotificationChannel.getImportance`.
- Replaces `java.util.Base64` mapper use with `android.util.Base64` and `Base64.NO_WRAP` to preserve encoding while supporting the app minSdk.
- Extends the D8 verifier to reject both regressions.

D8 r6 lint-floor hotfix keeps every prior D8 fix and replaces Android UI java.time calls with minSdk-23-safe java.text/java.util date helpers in progress, schedule, and sessions screens.
