# TVOV03 Android TV pairing, control, and cast protocol foundation

TVOV03 adds the shared phone-to-TV control protocol without adding a transport stack.

## Changes

- Added `:core:tvcontrol` as a pure Kotlin serialization module.
- Added `StudyFlowTvPairingToken` for explicit, revocable, short-lived pairing.
- Added `StudyFlowTvSurface` for controllable TV destinations.
- Added `StudyFlowTvControlAction` and `StudyFlowTvControlCommand` for phone-driven remote control.
- Added `StudyFlowTvCastState` for StudyFlow presentation-state casting.
- Wired both the mobile app and TV app to the shared module so schemas cannot drift.
- Added `verifyStudyFlowTvPairingControlCastProtocol` and serialization round-trip tests.

## Non-goals

TVOV03 does not add Cast SDK, MediaRouter, network discovery, raw screen mirroring, AndroidX TV UI, Compose Runtime, phone feature modules, `core:data`, or `core:database` to `:app-tv`. Transport and UI pairing flow arrive in later overlays.
