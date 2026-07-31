# Phase 2 Acceptance Contract

Phase 2 is accepted when all of the following are true:

- `:core:database` is an Android library using Room and KSP.
- KSP and Serialization are applied through convention plugins backed by one build-logic classloader.
- AGP built-in Kotlin and the AGP new DSL remain enabled.
- Android modules do not apply `org.jetbrains.kotlin.android`, KSP, or Serialization directly.
- `StudyFlowDatabase` exports schema version 1.
- The domain model covers every root listed in the implementation roadmap.
- Every plan-owned Room entity contains `studyPlanId`.
- Every plan-owned Room entity cascades when its owning `StudyPlan` is deleted.
- Optional cross-entity links use `SET_NULL` where retaining history is safer.
- Room stores stable English enum names instead of translated labels.
- `RoomStudyPlanRepository` implements the existing `StudyPlanRepository` contract.
- The application container uses `RoomStudyPlanRepository` rather than the in-memory repository.
- Initial sample seeding occurs at most once and does not resurrect deleted plans.
- Plan create and replace operations execute in Room transactions.
- A cascade integration test covers plan, subject, and topic deletion.
- Mapper tests cover round trips and Phase 1 timestamp normalization.
- `.devtool.toml` validates the database, data, and application modules.
- The root Gradle Kotlin DSL quality filter iterates over `File.toPath()` segments and contains no receiverless `none` call.
- `:core:testing` remains a plain JVM module and does not depend on Android modules.
- Repository validation rejects every JVM-to-Android project dependency.
- `scripts/verify_phase_2.py` passes.

## AGP 9 plugin topology

- Android modules use AGP built-in Kotlin.
- The AGP new DSL remains enabled.
- Android convention plugins never apply `org.jetbrains.kotlin.android`.
- KSP is applied only through the Room convention plugin.
- KSP, Compose, and Serialization are loaded by the included convention build.
