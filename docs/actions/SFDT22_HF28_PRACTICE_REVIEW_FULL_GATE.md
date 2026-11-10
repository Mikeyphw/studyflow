# SFDT22 HF28 — Practice and review real-journey failure isolation

## Grounded evidence

Exact-SHA full Android 36 run `42855563426` at `0c5ceaa2cdd213900612f8f4e53c4dc4ae3acc1a` failed on the FOURTH real UI journey (`realUserAnswersQuestionAndOpensReviewQueue`) with `compose_wait_timeout` and no guarded stage. Source, APK compilation, 2 Room regressions, 3 SFLP regressions and the first 3 UI journeys passed. The fifth journey, seven screenshots, and new-PID cold restart did not qualify. The observed root cause within the fourth journey is not yet identified; no production behavior is altered without evidence.

## HF28 remediation

- Keep every existing production Compose control and actual app navigation.
- Wrap all 25 practice/review actions in distinct, allowlisted `SFDT22_STEP_*` failures; no exception messages, user data or raw logs enter receipts.
- Require the real asynchronous practice-test creation to expose its actual `start_or_resume` control before clicking it. Never seed or synthesize an attempt.
- Preserve real answer-operation acknowledgment, attempt finishing, flashcard persistence, and both practice/review screenshots.
- Retain all 5 UI journeys, 7 screenshots, 2 Room and 3 SFLP regressions, independent verification, Android 36, signed debuggable APK identity and cold-process restart.
- Reject stale historical SHA pins and any relaxations in the full workflow.

## Scope and qualification

This patch strengthens the hosted acceptance test and its diagnostics. It does not establish that the underlying practice failure has been resolved. A new hosted full run is required and is release-nonqualifying unless the entire independent source + emulator + gate chain passes. PR #15 remains unmerged.
