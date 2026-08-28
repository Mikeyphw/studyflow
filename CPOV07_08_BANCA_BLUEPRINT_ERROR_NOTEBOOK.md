# CPOV07 + CPOV08 — Banca Blueprint Mock Scoring + Error Notebook 2.0

This combined overlay advances the public-exam specialization without a Room schema bump.

## CPOV07

- Adds typed `ExamBlueprint`, `ExamBlueprintSection`, and `ExamScoringPolicy` contracts.
- Supports no-penalty, fixed-negative, right-minus-wrong/Cebraspe-like, and weighted/custom scoring.
- Keeps unanswered questions neutral by default.
- Previews exact section supply and refuses creation when the bank cannot satisfy the blueprint.
- Freezes the blueprint into the existing MockExam `configurationJson` metadata when the mock is created.
- Final submission scoring reads only the frozen blueprint, so later UI/filter changes cannot mutate scoring history.
- Keeps question references in the existing `questionIds=` lane so portability and plan duplication remain compatible.

## CPOV08

- Reuses the existing `ErrorCategory`, `ErrorEntry`, and `ReviewItem` models.
- Adds quick error reclassification and category-count analytics to the wrong-answer notebook.
- Maps every existing error category to an explicit remediation action.
- Adds a versioned public-exam review-policy field inside existing Study Plan `configurationJson`.
- Defaults to 1/7/30-day error reviews, with recurrence-based urgency for the first pending review.
- ERROR_BASED review completion advances through the configured intervals.
- Existing repository compare-and-set and pending-review checks continue to prevent duplicate review storms and stale retries.

## Persistence

Room remains at schema 12. No duplicate mock, mistake, or review store is introduced.
