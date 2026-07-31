# StudyFlow Phase 4+5 Bridge r2

This bridge applies the Phase 4 portability/snapshot system and the Phase 5 syllabus/content system directly to the uploaded Phase 3 baseline.

## r2 fixes

- Adds `feature/subjects/consumer-rules.pro`, because the Android library convention plugin always declares `consumerProguardFiles("consumer-rules.pro")`.
- Leaves `.devtool.toml` untouched to avoid validation-plan drift and local configuration conflicts.
- Keeps `PlanPortabilityScreen` resource lookups composition-aware through `stringResource` and `pluralStringResource`.
- Narrows the `LocalContextGetResourceValueCall` suppression to `PlanPortabilityScreen`, where `LocalContext` is used only for SAF `ContentResolver` streams, not for localized resource lookup.

## Included phases

- Phase 4: versioned `.studyplan` ZIP import/export, package validation, snapshots and restore.
- Phase 5: subject/topic management, syllabus import, derived progress, Room schema `1 -> 2`.
