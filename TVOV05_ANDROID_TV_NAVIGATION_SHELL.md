# TVOV05 — Android TV navigation shell

TVOV05 converts the TV placeholder into a structured top-level navigation shell. The TV app now
has one explicit destination model shared by the D-pad UI and the paired-phone control protocol.

## Implementation

- Adds `StudyFlowTvDestination` and `StudyFlowTvNavigationShell` under `app-tv`.
- Builds a native Android nav rail with Home, Today, Practice, Reviews, Mock Exam Studio,
  Progress, Schedule, and Settings.
- Maps each TV destination to a `StudyFlowTvSurface` from `:core:tvcontrol`.
- Exposes `openCommandFor(...)` so paired-phone navigation and local TV navigation use the same
  destination contract.
- Adds deterministic focus items for the navigation rail using the TVOV04 focus primitives.
- Handles Back from any destination by returning to Home before leaving the activity.

## Size and dependency boundary

TVOV05 does not add Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature-module
fanout, `core:data`, or `core:database` to `:app-tv`.

## Validation

- `verifyStudyFlowTvNavigationShell`
- `:app-tv:testDebugUnitTest`
- `:app-tv:compileDebugKotlin`
- `:app-tv:assembleDebug`
- `:app-tv:lintDebug`
