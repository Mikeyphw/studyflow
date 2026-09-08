# TVOV13 — Android TV read-only schedule

TVOV13 implements the Android TV read-only schedule surface as a full roadmap unit.

Delivered scope:

- `StudyFlowTvScheduleSurface` model with today/week schedule context.
- Schedule items for review windows, study blocks, practice, mock-exam rehearsal, and recovery buffers.
- Explicit read-only contract: no edit, import, delete, reschedule, SAF, Room, repository, or file-management behavior in `:app-tv`.
- D-pad focus items for schedule cards and actions.
- Paired-phone command generation through `:core:tvcontrol`.
- Cast-state emission for `StudyFlowTvSurface.SCHEDULE`.
- Native shell rendering in `StudyFlowTvActivity`.
- Unit tests and `verifyStudyFlowTvReadOnlySchedule`.

The TV surface displays schedule presentation state and opens existing TV flows. Authoritative schedule mutation remains owned by the phone/tablet app and shared data layer.


## Roadmap carry-forward

This overlay also preserves the complete TV roadmap and working preferences in
`TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md`, with `verifyStudyFlowTvRoadmapInstructions`
checking that future TV work remains structured, target-correct, and dependency-budgeted.
