# WEB-W08/W09 — Schedule intelligence and Practice

## W08 schedule intelligence

StudyFlow now computes a **preview before apply** from persisted local facts: overdue review records, the explicit study cycle, topic priority, and already planned activities. Preview generation is pure and carries the state revision. Applying a stale preview is refused. Applying a preview only adds or moves **planned** activities; completed/cancelled sessions and evidence are immutable history and are never rewritten.

An overdue review is treated as a scheduling signal only because a persisted review exists. Unknown weakness, readiness, incidence or mastery is never invented.

## W09 Practice

Questions are local domain records. Every submitted answer produces a `question-attempt` Evidence record. Questions without an answer key are explicitly ungraded: StudyFlow records the attempt but does not manufacture correctness.

A wrong graded answer can create one due review and an Error Notebook entry with a user-selected category. Repeated mistakes in the same open category increment recurrence instead of creating a duplicate due-review storm. Reviews can be completed into evidence; Error Notebook entries can be resolved without deleting history.

State schema v3 adds Error Notebook persistence. Existing v2 and v1 local state migrates forward.
