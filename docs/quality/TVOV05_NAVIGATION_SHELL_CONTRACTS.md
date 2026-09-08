# TVOV05 navigation shell contracts

TVOV05 must keep TV navigation structured and shared between local remote use and paired-phone
control.

## Required contracts

- TV top-level navigation is owned by `app-tv`, not phone/tablet `:app`.
- The TV destination set is a small enum, not Boolean route flags or free-form screen names.
- The destination set includes Home, Today, Practice, Reviews, Mock Exam Studio, Progress,
  Schedule, and Settings.
- The navigation shell maps destinations to `StudyFlowTvSurface` values from `:core:tvcontrol`.
- Paired-phone open commands use the same destination model as local D-pad navigation.
- Focus items for the nav rail use TVOV04 focus primitives.
- Back returns to Home before leaving non-Home TV destinations.
- `:app-tv` remains dependency-budgeted and does not pull Compose Runtime, AndroidX TV UI,
  Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database`.
