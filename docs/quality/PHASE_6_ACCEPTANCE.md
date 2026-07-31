# Phase 6 Acceptance Contract

Phase 6 is accepted when the repository provides real schedule and study-strategy management without data leakage between Study Plans.

## Required behavior

- `:feature:schedule` exists and is reachable from the dashboard.
- Schedule strings have English and pt-BR parity.
- `StudyScheduleRepository` exposes schedule observation and mutations.
- `ManageStudySchedule` validates strategies, rules and planned activities.
- `StudySchedulePlanner` generates weekly activities from enabled rules.
- Rebalancing missed activities does not move locked activities.
- Room persistence remains encapsulated inside `:core:data`.
- `:app` does not depend directly on `:core:database`.
- The Phase 4+5 unused subject-string warnings are removed.
- Prior Compose scoped-modifier and LocalContext resource lookup regressions remain sealed.

## Validation

Run:

```bash
python3 scripts/verify_phase_6.py
```

Destination validation should also include focused Gradle tasks:

```bash
--task :core:domain:test \
--task :core:database:testDebugUnitTest \
--task :core:data:testDebugUnitTest \
--task :feature:schedule:testDebugUnitTest \
--task :feature:subjects:testDebugUnitTest \
--task :feature:plans:testDebugUnitTest \
--task :feature:dashboard:testDebugUnitTest \
--task :app:testDebugUnitTest
```
