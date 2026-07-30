# ADR-0001: Study Plan as aggregate root

- Status: Accepted
- Date: 2026-07-30

## Decision

`StudyPlan` is the aggregate root for every piece of study-specific data. Child entities carry a
stable `studyPlanId` and cannot silently migrate between plans.

## Consequences

- Plan deletion and export have explicit boundaries.
- Multiple objectives can coexist without statistical leakage.
- Templates can strip personal history while preserving structure.
- Future synchronization can partition work by plan.
