# TVOV07 Android TV Today workspace

TVOV07 implements the Today workspace as a real Android TV surface backed by a structured Kotlin model and native Android rendering. It shows the current plan context, next study block, due reviews, practice queue, weekly progress, and mock-exam preparation entry without relying on phone-only feature modules or database access inside the TV APK.

## Delivered contracts

- `StudyFlowTvTodaySnapshot` carries the TV-ready workload summary.
- `StudyFlowTvTodayWorkspace` maps Today actions to concrete TV destinations.
- Every Today card exposes a D-pad focus item.
- Every Today action can be represented as a shared phone-to-TV `StudyFlowTvControlCommand`.
- The Activity renders the Today workspace as first-class TV content, not as a placeholder string.

## Validation

- `verifyStudyFlowTvTodayWorkspace` checks source, tests, strings, Gradle task wiring, Devtool target wiring, and documentation.
- `StudyFlowTvTodayWorkspaceTest` validates snapshot constraints, destination routing, focus contracts, and paired-phone commands.
