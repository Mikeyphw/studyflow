# TVOV16 — Cross-device control/state continuity

TVOV16 adds a deterministic, transport-free command/state layer for the Android TV presentation.
`StudyFlowTvControlSession` and `StudyFlowTvControlContinuityState` live in `:core:tvcontrol`, while
`StudyFlowTvControlStateReducer` owns the TV-side transition logic.

Both `LOCAL_REMOTE` and `PAIRED_PHONE` origins reduce commands into the same in-memory TV surface
model. The reducer maps Home, Today, Study Session, Question Practice, Review, Mock Exam, Progress,
Schedule, Library, and Settings. Its continuity projection includes current destination/surface,
active title, active item identifier, playback state, monotonic command revision, last command, and
pairing expiry information.

Duplicate and stale commands fail closed. Paired-phone commands also fail closed after revocation or
expiry. Expired sessions are moved to the existing revoked pairing state. No socket, discovery,
MediaRouter, Cast SDK, or other real network transport is introduced here; transport remains deferred
to a later roadmap decision under the TV size budget.

Validation is provided by core protocol/continuity tests, app-tv reducer tests, and
`verifyStudyFlowTvControlStateContinuity`.
