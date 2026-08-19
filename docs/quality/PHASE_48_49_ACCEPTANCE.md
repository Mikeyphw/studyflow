# Phase 48-49 Acceptance

## Source acceptance

- `ExamReadinessScore` and readiness signals exist in the model layer.
- `ManageExamReadiness` combines analytics, schedule, and mock-exam repositories.
- The readiness engine keeps scores in the 0-100 range and exposes an explicit readiness band.
- `AdaptiveReschedulePreview` is generated before any mutation.
- `ManageAdaptiveReschedule.applySafePreview` updates only executable existing activities.
- Locked activities are not rewritten.
- The Progress workspace displays readiness and adaptive reschedule previews.
- English and pt-BR workspace resources have matching keys.
- Repository contracts include the Phase 48-49 verifier.

## Target-host acceptance

- Gradle/AGP compile and lint must pass on the configured JDK 21 target host.
- Unit tests for readiness and reschedule planner must pass.
- Manual verification should cover overdue reviews, missed blocks, low accuracy, locked activities,
  and a stable plan with no proposed mutations.
