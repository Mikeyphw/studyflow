# StudyFlow HF30 HF09 — Real practice-test readiness and diagnostics

## Evidence / provenance

Source-pinned PR #16 commit `f1429d557a606f97e11cafb00a07a63c6c82c8dc`, hosted API36 run `43111844519`:
- SFDT22 source and trust checks: PASS.
- HF07 five real Room Android test methods: PASS.
- First three real UI journeys (plan, subject/topic, session): PASS.
- Fourth real user journey, `realUserAnswersQuestionAndOpensReviewQueue`: FAIL at `practice_test_ready`, `compose_assertion`.
- Fifth cold-start persistence journey and seven-screen qualification: NOT RUN/NOT QUALIFIED.

The production practice UI creates a test asynchronously and renders test launch actions in
`RecentTestsCard`, below a long `LazyColumn`. The previous test called `visible()` immediately.
Its one-time `seek()` scroll could run before the Room Flow emitted the new test, and its
failure was a generic Compose assertion. The precise prior failing condition (persistence
versus off-screen button) cannot be recovered from the previous minimized artifact.

## Repair

1. `practice_test_ready` checks that *exactly one* unsubmitted matching StudyTest
   exists in the real app SQLite database using a read-only SQL query with bound values.
2. It then retries scrolling the **real** production LazyColumn to the recent-tests card
   and its actionable, enabled "Start or resume" button. No test fixtures or mocked domain
   operations are used. An operation-failure snackbar causes fail-closed refusal.
3. The unchanged UI continuation must still **click** the real Start button, answer,
   finish, save a flashcard, capture seven screenshots, and verify a new process after
   force-stop. A persisted row or visible snackbar is never sufficient to pass.
4. Three distinct whitelisted failure categories are added: creation failure, missing
   persistent row, and missing/action-not-visible UI. User content and SQL values stay
   on the emulator and are never exposed in GitHub diagnostic artifacts.
5. Strict SHA-256 source pins and a negative-regression Python contract run before any emulator.

No production changes, review shortcuts, release signing, merged PR, local Gradle,
forced branch updates, or new PR branches. Offline checks are **not** Android qualification.
