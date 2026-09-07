# TVOV00 architecture contracts

TV support starts as a small-TV-APK plan, not a universal-APK bloat plan and not a fork.

## Contracts

1. `studyflow_tv` exists in `.devtool.toml` immediately.
2. `studyflow_tv` is source-contract-only while `:app-tv` does not exist.
3. The eventual TV release APK has a 25 MiB target and a 35 MiB hard ceiling.
4. Shared modules do not depend on Android TV UI libraries.
5. Phone/tablet modules are not asked to run TV packaging tasks.
6. Future `:app-tv` must reuse shared core models, repositories, portability, and navigation.
7. The Android mobile app is a first-class paired controller for every TV destination.
8. Cast mode sends StudyFlow intents and presentation state, not raw database rows or arbitrary UI
   trees.
9. Pairing must be explicit, revocable, and session-scoped.
10. Phone-to-TV control must not add TV UI dependencies to shared or phone-only modules in TVOV00.

## Validation

- `scripts/verify_tvov00_architecture.py`
- `scripts/verify_tv_size_budget.py`
- `scripts/verify_tv_control_cast_contract.py`
- Gradle tasks: `verifyStudyFlowTvArchitecture`, `verifyStudyFlowTvSizeBudget`,
  `verifyStudyFlowTvControlCast`
- Devtool target: `studyflow_tv`
