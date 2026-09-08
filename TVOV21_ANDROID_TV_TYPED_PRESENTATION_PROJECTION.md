# TVOV21 — Typed TV presentation snapshots and canonical data projection

TVOV21 closes the roadmap audit's data-truth gap without moving database ownership into the TV APK.
The shared `:core:tvcontrol` module now defines a serializable, transport-neutral presentation
snapshot for Today, active study session, question practice, mock exam, review, progress, schedule,
and limited library/reference content.

The data-owning Android phone process exposes `StudyFlowTvPresentationSource`. It observes the real
StudyFlow repositories already owned by `DefaultAppContainer` and projects canonical domain models
through `StudyFlowTvPresentationProjector`. IDs from questions, planned activities, review items,
mock exams, and library materials cross the boundary as presentation IDs; Room entities, database
rows, file handles, and arbitrary Android UI trees do not.

The TV Activity no longer uses the historical `defaultState()` / `defaultSnapshot()` demo fixtures as
its runtime presentation source. It starts from an explicit waiting state and renders typed snapshots
through `StudyFlowTvPresentationAdapter`; absent surfaces render a truthful no-data/waiting message.
The old defaults remain only as backwards-compatible unit-test/model fixtures for earlier TV overlays.

TVOV21 deliberately does **not** implement pairing or a wire transport. The production phone-side
projection source and TV-side inbox are the two ends that TVOV22 will connect. The existing `app-tv`
dependency boundary therefore remains intact: no `core:data`, `core:database`, Room, feature-module,
Cast SDK, MediaRouter, or networking dependency is added to the TV APK.

The audited Home `PAIR_PHONE` command is also corrected to route to the Settings pairing surface
instead of producing an unhandled Home target.
