# TVOV06 Home Surface Contracts

The Android TV Home surface must remain remote-first, paired-phone aware, and size-budgeted.

## Required

- Home is the default TV destination.
- Home has structured cards for Today, Practice, Reviews, and Mock Exam.
- Home exposes a Pair Phone action.
- Home actions use the shared TV destination/control protocol.
- The TV app keeps D-pad focus primitives for cards and actions.
- TVOV05 unused string resources are removed.

## Forbidden in TVOV06

- Compose Runtime in `:app-tv`
- AndroidX TV UI dependency
- Cast SDK dependency
- MediaRouter dependency
- `core:data` dependency
- `core:database` dependency
- phone feature-module fanout
