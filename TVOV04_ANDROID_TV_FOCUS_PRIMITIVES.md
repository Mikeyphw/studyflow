# TVOV04 Android TV D-pad and focus primitives

TVOV04 adds pure Kotlin D-pad/focus primitives to the minimal `:app-tv` shell.

## Changes

- Added `StudyFlowTvRemoteCommand` for Up/Down/Left/Right/Select/Back/Play-Pause.
- Added `StudyFlowTvRemoteKeyMapper` for Android remote key codes.
- Added `StudyFlowTvFocusRole` and `StudyFlowTvFocusItem` as small focus metadata.
- Added `StudyFlowTvFocusPolicy` with deterministic initial focus, disabled-item skipping, non-wrapping directional movement, and focus retention for Select/Back/Play-Pause.
- Added `applyStudyFlowTvDpadFocus()` as a native Android View helper with a 64dp minimum target.
- Added visible focus drawable state for TV action controls.
- Updated the placeholder TV activity with a focusable action row and deterministic initial focus.
- Added focused unit tests and `verifyStudyFlowTvFocusPrimitives`.

## Dependency stance

TVOV04 still does not add Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database`. The TV APK remains size-first while gaining a concrete remote-control foundation.
