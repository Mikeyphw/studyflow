# TVOV25 — Android TV domain truth and persistence convergence

TVOV25 removes the remaining split-brain behavior between TV presentation state and StudyFlow's canonical phone-owned persistence.

## Canonical mutation boundary

The shared `:core:tvcontrol` module now defines transport-neutral `StudyFlowTvDomainAction` and `StudyFlowTvDomainActionResult` contracts. A mutation is resolved from the current typed TVOV21 snapshot, so Study Plan, session, test, question, mock-exam, review, and planned-activity identifiers are canonical rather than TV fixture ids.

The Android phone owns execution. `DefaultStudyFlowTvCanonicalMutationGateway` delegates to the existing `ManageStudySession`, `ManageStudyPractice`, and `ManageStudyReview` APIs plus their existing repositories. The TV APK does not gain Room, `core:data`, `core:database`, or repository access.

Supported canonical mutations include Study Session start/pause/resume/complete, Practice answer/finish/mark-for-review, Mock Exam start/submit, Review grade, and question-backed review error evidence. Mock scoring is whatever the existing StudyFlow practice engine persists; TVOV25 contains no scoring formula.

## D-pad and paired-phone path

TV-originated persistent commands enter `StudyFlowTvDomainActionOutbox`. The paired phone reads the oldest pending action via the existing authenticated TVOV22 continuity exchange, executes it, and returns `DOMAIN_ACTION_RESULT`. Only the matching action id is acknowledged and removed.

Phone-controller persistent commands use the same mapper/executor directly and do not first mutate TV-local pseudo-domain state. Normal navigation/explanation/section focus can remain presentation-local because those actions do not create StudyFlow evidence.

## Idempotency and projection round trip

`StudyFlowTvDomainActionExecutor` checks an app-private result journal before executing. Normal reconnect/retry of the same action id is therefore idempotent: it returns the recorded result rather than applying the mutation again. The existing backup-disabled app policy keeps this journal local.

After persistence, existing repository flows update TVOV21's canonical projector. TVOV24 then delivers the new snapshot and its exact receipt to TV. The TV is therefore updated by persisted state rather than optimistic mutation.

## Removed pseudo-domain behavior

TV state helpers no longer synthesize answer selection, review-known/error completion, Study Session lifecycle completion, elapsed-time jumps, or a fixed mock score. Mock pause/resume is not presented as persistent functionality because the canonical MockExam model has no pause state.

## Remaining boundary

TVOV26 owns final real-phone + real-TV end-to-end acceptance, including release runtime behavior, pairing/control/cast, local D-pad persistence, reconnect/restart/resync, and revoke/expiry checks.
- TV-originated canonical mutations continue to drain while paired even when presentation casting is stopped; D-pad persistence is not coupled to cast state.
