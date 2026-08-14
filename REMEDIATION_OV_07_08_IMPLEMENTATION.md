# StudyFlow OV-07 + OV-08 Remediation

This intermediate overlay implements the locked remediation roadmap scopes:

- OV-07 / RM-026–RM-031: question import and retention safety; practice/TestMode/timing/terminal submission; mock exam correctness; SRS completion evidence; wrong-answer remediation; weakness drills.
- OV-08 / RM-032, RM-033, RM-034, RM-045: authoritative goal progress; analytics/readiness/coaching evidence; Unicode-aware live search with ID-bearing navigation; exam-report readiness, full evidence counts, provenance, and measured PDF wrapping.

## Production-path acceptance points

### OV-07
- Question imports are fully previewed/validated before one atomic repository write; the UI preview preflights current-plan Subject/Topic ownership and mismatched topic/subject pairs, and invalid MCQ answers or invalid explicit fields are rejected rather than coerced. Semantic duplicate identity includes normalized type/options/answer/explanation/board/source/year rather than statement+answer only.
- Hard deletion refuses questions retained by saved Test/Mock attempt history.
- Practice and Mock attempts have explicit start, open-attempt CAS answer writes, one terminal CAS submit, deadline handling, real per-question elapsed time, behavioral TestMode explanation rules, total-question scoring, finite/non-negative negative marking, and pass thresholds. Submitted Practice evidence/report percentages use the same total-question denominator, so partial attempts cannot reintroduce an answered-count score inflation downstream.
- Wrong answers are captured transactionally with terminal submission and merged into one recurring error plus at most one pending remediation review.
- SRS grading terminalizes immutable completion evidence and creates a separate scheduled successor; suspended flashcards suppress/cancel active review work; local-day completion and one canonical due policy are shared across consumers. Review execution resolves actual Question/Flashcard/Error/Topic content rather than topic IDs/method labels.
- Weakness drills use current question ownership, canonical due semantics, transactionally join an already-open drill with the same question-set key, preserve submitted drill history under distinct attempt IDs, and consume time-reactive review/notebook inputs.

### OV-08
- Non-CUSTOM goals derive current progress from authoritative session/test/mock/topic evidence and current local period; completed goals leave active lists, archived goals can be restored, and manual progress cannot overwrite evidence-backed goals.
- Analytics aggregate session and submitted-test question evidence, use real completed-review rows, local calendar windows, explicit weak-area thresholds, and avoid counting a linked completed activity and StudySession twice in the evidence timeline.
- Weekly coaching filters the complete authoritative evidence timeline to the user's local Monday–Sunday week rather than using a recent-12 display sample.
- Search ranking normalizes text with Unicode-compatible Kotlin normalization; production search candidates are re-ranked in Kotlin, results are re-resolved by `(type, planId, id)` before navigation, target IDs are carried to destination workspaces, and search/recommendations react to the Room graph revision plus time ticks.
- Exam report export remains disabled until cleanup, analytics, and readiness are Ready; the report receives the full evidence count independently of its display sample, uses canonical review-due semantics, records metric provenance, and PDF text wrapping is width-measured so long unbroken tokens are split safely.

## Post-build contract audit corrections

A second promise-by-promise audit found and corrected gaps before application: import preview ownership was missing, topic reviews still displayed fallback identifiers, weakness-drill construction contained a duplicate local declaration and reused submitted drill identity, and Mock Exam negative marking accepted non-finite input. The Mock Exam Studio now exposes a validated finite/non-negative penalty field so negative marking is reachable from the normal production creation flow. Dead attempt-replay wrong-answer capture helpers were removed so terminal submit remains the sole attempt-provenance capture path. Search stale-result removal now keys by `(type, planId, id)`, and targeted flashcard results are rendered even when that flashcard has no currently-due review. Targeted regressions were added for import ownership, model/request finite negative marking, and weakness-drill identity/history behavior.

## Deferred validation

The artifact retains a relevant final validation task set in its Devtool schema-v2 manifest. This overlay is intermediate and is intended to be applied with `--no-validate`; Gradle, lint, Release, instrumentation, and device validation remain deferred to the last roadmap overlay.
