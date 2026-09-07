# TVOV02 Android TV dependency budget and lint cleanup

TVOV02 removes the initial app-tv lint warnings while keeping the TV APK small.

## Changes

- Added explicit `dataExtractionRules` and legacy `fullBackupContent` resources with backup disabled.
- Removed `resizeableActivity` because it is minSdk-guarded and unnecessary for the TV shell.
- Removed fixed `screenOrientation` because Android 16+ ignores most fixed-orientation requests and StudyFlow TV should adapt instead.
- Replaced the large vector banner with a lightweight `layer-list` drawable to avoid vector-raster overhead.
- Added `verifyStudyFlowTvDependencyBudget` and wired it into the `studyflow_tv` target.

## Dependency stance

TVOV02 still does not add Cast SDK, MediaRouter, AndroidX TV UI, Compose Runtime, phone feature modules, `core:data`, or `core:database` to `:app-tv`. Those dependencies must be introduced only with a measured size delta and a focused verifier.
