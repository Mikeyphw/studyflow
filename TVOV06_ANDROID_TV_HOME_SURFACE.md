# TVOV06 — Android TV Home Surface

TVOV06 turns the TV shell into a structured Home surface instead of a generic placeholder panel.

## Implementation

- Adds `StudyFlowTvHomeSurface` as the default TV entry model.
- Adds stable Home cards for Today, Practice, Reviews, and Mock Exam.
- Adds primary Home actions for Continue, Pair Phone, and Practice.
- Routes Home actions through the existing TV navigation and `:core:tvcontrol` protocol.
- Updates `StudyFlowTvActivity` to render a real Home card section.
- Removes unused TVOV05 strings that were reported by lint.

## Boundaries

TVOV06 does not add Compose, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature modules to `:app-tv`.

## Validation

- `verifyStudyFlowTvHomeSurface`
- `:app-tv:compileDebugKotlin`
- `:app-tv:testDebugUnitTest`
- `:app-tv:assembleDebug`
- `:app-tv:lintDebug`
