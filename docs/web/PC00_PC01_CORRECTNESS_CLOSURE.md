# PC00 / PC01 correctness closure

This parity-closure overlay fixes two correctness defects found after the W19 release audit. It does not broaden product scope.

## PC00 — mock scoring

Mock scoring now uses the exact frozen preview selection. Explicit blueprint sections contribute their configured section weight; within a section, positive question points distribute that section weight proportionally. Without explicit sections, positive question points are the maximum-point values directly. Missing/non-positive point metadata falls back to one point.

Scoring policies are explicit: `no-penalty` and `weighted` award the available value for correct answers and zero for wrong answers; `fixed-negative` subtracts `negativePoints × question value`; `right-minus-wrong` subtracts the full question value. Unanswered questions are always neutral. Percentages clamp the total earned value to zero for reporting and round to the nearest 0.01 percentage point. Raw score is retained separately and can be negative.

Recorded mock evidence includes the scoring policy, maximum score, raw score, percentage, per-section totals, and per-question outcome/value breakdown. The Concurso cutoff gap continues to derive from the recorded percentage.

## PC01 — review progression

The public-exam review policy accepts one to eight non-decreasing whole-day intervals between 1 and 365 days. The default remains `1, 7, 30`, and the React Concurso workspace exposes the policy for editing.

A wrong graded question schedules stage zero using the first configured interval. Completing a review with grade 3–5 advances one stage, clamped at the final interval. Grade 0–2 resets the next review to stage zero; failed reviews reset progression to the first configured interval. Every completion creates a new due successor while preserving the completed review as history. Existing due reviews for the same source are reused instead of creating duplicate storms. Open Error Notebook entries follow the active successor review.

Legacy v5 review records remain valid: loading normalizes missing progression fields with safe defaults, so the top-level state schema remains v5 and portable format v1 remains compatible. Review-completion evidence records the grade, policy snapshot, current interval, successor interval, successor ID, and next due timestamp.
