# Phase 52-53 r4 compile hotfix audit

This supersedes the r2 package after Devtool rejected it at the source-hash gate. The code payload is rebased onto `studyflow-20260806-141421.tar.gz`, preserving the landed Phase 50-51 r3 Kotlin compile-hotfix documentation and the local-value `reviewTitle` source shape.

## Rebase fixes

- Updated all modify `source_sha256` values against the fresh post-50/51 r3 archive.
- Merged the Phase 52-53 changelog and validation-report entries without deleting the Phase 50-51 r3 hotfix notes.
- Kept the r2 Mock Exam Studio audit fixes: disputed-question exclusion by default, board/source/tag filters, dynamic preview, and full warning display.
- Kept the Phase 53 widget action routing and accessible action row.

## Previous r2 audit summary

# Phase 52/53 Delivery Audit r2

## Verdict

The first Phase 52/53 overlay delivered the basic Mock Exam Studio and Today widget action row, but the follow-up audit found two functional gaps that were too important to leave as documentation-only promises.

## Gaps found

1. **Disputed-question filtering was implied but not enforced.** `MockExamStudioRequest.includeDisputed` existed, but disputed questions were still eligible when the flag was false.
2. **Source, board, tags, and disputed filters were domain-only.** The Studio UI could create a timed mock but did not expose the filtering controls that make the planner's source/exam-board/tag ranking useful to learners.
3. **The dialog preview did not update as the learner changed mock settings.** It showed the initial section/selection count rather than the current requested filter set.
4. **Only the first warning was displayed in the dialog.** Multiple simultaneous warnings, such as skipped ineligible questions plus narrow coverage, could be hidden.

## r2 fixes

- Excludes disputed questions unless `includeDisputed` is true.
- Adds UI controls for exam board, source, comma-separated tags, and disputed inclusion.
- Recomputes the preview as the learner edits count, timer, filters, or disputed inclusion.
- Displays every current warning inside the Studio dialog.
- Adds a domain test for source filtering and disputed exclusion/inclusion.
- Hardens `scripts/verify_phase_52_53.py` to require the new behavior, UI controls, strings, and tests.

## Remaining external validation

This audit used source validators and clean-apply simulation only. Devtool/Gradle remains the authority for Android compilation, lint, packaging, and widget resource validation.

## r4 Kotlin compile hotfix

Devtool r3 reached `:core:domain:compileKotlin` and found that `MockExamStudioPlanner` used `Question::isExamEligible` for an extension function reference. Kotlin prohibits this member-and-extension reference shape. R4 replaces it with an explicit lambda, `.filter { question -> question.isExamEligible() }`, and the Phase 52/53 verifier now rejects the unsafe reference.
