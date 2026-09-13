# OV02 — Session & practice canonical invariants

**Roadmap:** Overlay 2 of 30  
**Finding ownership:** S1-01, S1-02, S1-04, S1-05, S1-11, S1-12

OV02 repairs the shared session/practice correctness boundary before later planner, persistence, portability and SFLP remediation layers build on it.

## Session timing and duration truth

`grossDurationSeconds` is retained for schema compatibility but is explicitly treated as the canonical persisted **active study duration**. `pauseDurationSeconds` is independent pause telemetry and downstream goal/readiness analytics no longer subtract it from active duration a second time. In-memory runtime accumulation is millisecond-based and monotonic so repeated pause/resume boundaries do not discard sub-second deltas before persistence.

The shared `StudyDomainInvariants` object now owns the duration/material contracts used by models and managers. General Study Sessions, scheduled blocks and planned activities accept 5–720 minutes; a strategy minimum accepts the previously manager-supported 5–480 range while the strategy maximum remains bounded at 720; preferred-session duration remains the distinct 5–240 preference contract. Relevant Android forms read those canonical ranges instead of restating contradictory literals. Materials consistently require a positive `totalUnits` when that value is present.

## Immutable practice and mock attempts

New attempts persist `questionSnapshotV1=` records containing the question identity and immutable execution/grading content. Rebuilding a started attempt uses the frozen snapshot rather than mutable Question-bank rows. Answer grading resolves the question from the frozen attempt state, so editing or deleting a bank question cannot silently change the active attempt's answer key.

Legacy unstarted attempts with a valid explicit question-ID record can be upgraded to frozen snapshots at start. Missing, duplicate or count-mismatched identity now fails closed. A started legacy attempt that has no immutable snapshot also fails closed because its original content cannot be reconstructed safely. The former fallback that silently selected the first current questions is removed.

Frozen snapshot question IDs are included in portability reference discovery/remapping alongside `questionIds=` and `answer=` records so private export/duplication cannot create an internally split identity set.

## Explicit Mock Exam Studio ownership

`MockExamStudioPlanner.preview` now requires an explicit `StudyPlanId` and rejects a mixed-plan bank. Mock creation also validates every selected question against the requested Plan. List ordering can no longer choose Plan ownership implicitly.

## Carry-forward OV01 correction

While exercising the validator, OV02 found that OV01's regenerated wrapper launcher used `java -jar gradle-wrapper.jar`; the supplied wrapper jar exposes `GradleWrapperMain` but no `Main-Class`. OV02 corrects both wrapper launchers to the standard classpath + `org.gradle.wrapper.GradleWrapperMain` invocation. This is a narrow carry-forward correction so S0-09 remains actually closed.

## Validation

Run `scripts/validate_ov02_session_practice_invariants.sh`. It first enforces source/ledger contracts, stops any existing Gradle daemon, then runs the targeted model/domain/portability JVM suites, Desktop tests and Android Kotlin compile gate. Full roadmap validation remains reserved for Overlay 30 of 30.
