# HF27 FIX-03 HF03 — isolated SFLP replay and two-signal Android acceptance

## Diagnosis and risk

HF02 built and passed three actual UI journeys, but the first SFLP replay regression still reported `runner_test_failed_unstaged`. The test itself was using the production **studyflow.db** and created a second, independently owned data-layer/Room handle, making the test unsound in the presence of the app's SFLP reconciliation driver. The absence of a stage string in the JUnit stack is not a reliable cause classification.

## Implementation

1. Isolate the SFLP Android test in `sfdt27-sflp-regression.db`, the same strategy as the existing Room settings regression. Each test starts after verifying deletion of the prior fixture and ends after cleanup, database close, and database file deletion.
2. Build the fixture with a real `Room.databaseBuilder` and the production `StudyFlowIntegrity.install` callback. Replay through the **same** `RoomStudyFlowSflpProductStateBridge` implementation, injected by a tiny debug-variant-only factory in `core:data/src/debug/`. There is one owned Room handle, not two; release sources and production database creation are unchanged.
3. Emit ASCII-only, allowlisted `SFLP_V1` test-stage records in app-private storage. Before each test, the host removes the prior private receipt using `run-as`. After the test, the host validates its test name, stage, order, total size and terminal result. No exception text, paths, plan identifiers, raw SQL or customer data are exported.
4. Pass **only** with both the existing exact JUnit start/finish/success contract **and** a fresh, test-bound terminal private receipt. Missing, oversized, forged, incomplete and ambiguous receipts fail closed. Failure stages remain red and carry only bounded stage codes.
5. Update two stale HF27 source contracts, pin all new and changed content hashes in the full gate, and run the new negative parser/fixture contract from both focused and full source jobs.

## Not changed

No production SFLP replay algorithm change. No production account, personal DB or user settings mutation. No fallback/skip/auto-merge/force-push/release qualification. Keep HF04, HF27 diagnostics, five full real UI journeys, seven distinct screenshots, new PID/force-stop proof and CI-only Gradle.

## Acceptance

The focused Android 36 runner still requires two preexisting Room regressions, three **real** SFLP tests, and three full UI journeys. The focused lane is explicitly nonqualifying: successful focus-only testing is never full Android qualification. The full gate additionally requires five journeys, screenshots, process identity and independent source/security evidence. PR #15 must remain unmerged.
