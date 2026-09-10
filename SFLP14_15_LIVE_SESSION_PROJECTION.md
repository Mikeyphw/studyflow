# SFLP14/SFLP15 live continuity and presentation projection

## Delivery status

SFLP14 live session continuity — delivered.
SFLP15 universal presentation projection — delivered.

## SFLP14 live session continuity

StudyFlow now models a live study session as a protocol-level object instead of a screen-local timer. The
`StudyFlowLiveSessionSnapshot` carries the workspace, activity, active executor, fencing token, session epoch,
last sequence, cursor, participants, completion device and completion operation. Every live update is a typed
`StudyFlowLiveSessionEvent` that can be carried by the SFLP envelope and written back to the SFLP02 journal.

The reducer enforces deterministic replay, monotonic event sequences, stale epoch rejection, duplicate event
suppression, pause/resume/cursor updates and exactly-once completion. A later duplicate completion cannot create
another completion operation, which preserves the SFLP03 session guards even when clients reconnect after offline
work.

## SFLP15 presentation projection

Presentation is now a universal source/sink protocol. Android, Desktop and Web can advertise
`device.presentation_source`; Web TV and Android TV can advertise `device.presentation_sink`. Projection offers are
explicitly target-gated when needed, carry content kind, entity, title, content hash and required sink capabilities,
and expire. TV-class receivers reject write-capability projection offers.

Projection frames are monotonic by sequence and acknowledged by the sink with frame ID, sequence and content hash.
This lets any source recover from a dropped response by checking the receiver acknowledgement instead of replaying
or duplicating presentation state.

## Wire names

- `session.live_start`
- `session.live_join`
- `session.live_heartbeat`
- `session.live_cursor`
- `session.live_pause`
- `session.live_resume`
- `session.live_complete`
- `presentation.projection_offer`
- `presentation.projection_accept`
- `presentation.projection_frame`
- `presentation.projection_ack`
- `presentation.projection_control`
- `presentation.projection_end`

## Platform bridges

Android gets `AndroidStudyFlowLiveProjectionBridge`; Desktop gets `DesktopLiveSessionProjectionController`; Android
TV gets `StudyFlowTvLiveProjectionBridge`; Web gets `StudyFlowWebLiveSessionContinuityController` and
`StudyFlowWebPresentationProjectionController`. These are bridges over the shared protocol types, not a new hub
path, and they preserve Android/Desktop/Web/TV peer symmetry.
