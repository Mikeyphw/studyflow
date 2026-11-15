# StudyFlow SFDT22 HF30 HF10 — committed practice answers and real search return

## Exact evidence

PR #16 parent `2d057e1652f434f78a60241312cc7bf0e802dd6a`.
- SFDT22 run 43118510550: source gate PASS; Room five real API36 methods PASS; first three of five real journeys PASS; fourth journey FAILED at `practice_answer_acknowledged` / `compose_wait_timeout`.
- SFDT21 run 43118510628: three of four real MainActivity tests passed; `realActivityOpensGlobalSearchAndReturnsToToday` failed with AssertionError. Prior receipt does not identify its internal failing step.
- No release or physical-device qualification was granted.

## Changes

1. `practice_answer_acknowledged` now proves the actual new practice test has `answeredCount=1` in read-only SQLite joined to the new Study Plan. The answer must be started, unsubmitted, and have one question. The asynchronous success snackbar may expire and is not the authoritative signal. An operation-failure snackbar or absent Room mutation fails closed with a distinct test marker. The actual True tap and later Finish, flashcard creation, screenshots and process restart remain mandatory.
2. SFDT21 Search uses the real accessible header action and waits for the real input field, uses real Android Back presses, confirms the field is gone, and checks Today tab selection. Seven test-only stable stage codes distinguish the exact failure step. The independent Python verifier emits only allowlisted failure stage tokens, never private values or raw logs.
3. Both hosted workflows verify the source SHA-256 of all changed instrumentation/verifier files and this contract. Offline negative tests reject stage/code spoofing, missing tests, source pin drift, skipped Android acceptance, raw private data and release bypasses.

## No qualification shortcuts

All five SFDT22 journeys, seven screenshots, real Room and SFLP regressions, four SFDT21 tests, exact Android API36, cold process restart, original release/signature gates, and the signed-vs-hosted boundary remain mandatory. No changes to production application source, live accounts, release signing, or CI permission elevation. The launcher operates only against exact clean PR #16 source with normal Devtool application and non-force Git push.

Local Python tests are *source contracts only*. This patch is not considered qualified until both relevant hosted emulator checks pass at the newly produced exact commit.
