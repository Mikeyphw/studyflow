# TVOV08 Android TV Study session experience

TVOV08 implements the Study session experience as a real TV surface opened from Today or paired-phone control. It adds a deterministic session state machine, visible segment plan, D-pad focus model, phone/remote command mapping, and cast-state output through the shared TV control protocol.

## Delivered contracts

- `StudyFlowTvStudySessionExperience` owns the Study Session surface.
- Session states cover ready, running, paused, and completed.
- Actions cover start, pause, resume, next segment, complete, and return to Today.
- TV remote Play/Pause toggles session playback on the Study Session surface.
- The paired phone can issue the same control actions through `StudyFlowTvControlCommand`.
- Cast state uses `StudyFlowTvCastState` and the `STUDY_SESSION` surface instead of raw database rows or UI mirroring.

## Validation

- `verifyStudyFlowTvStudySessionExperience` checks source, tests, strings, Gradle task wiring, Devtool target wiring, and documentation.
- `StudyFlowTvStudySessionExperienceTest` validates the state machine, focus model, command mapping, and cast-state emission.
