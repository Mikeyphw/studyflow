# TVOV16 cross-device control/state continuity contracts

- Shared session and continuity schemas live in `:core:tvcontrol`.
- Local remote and paired phone commands use one deterministic reducer and one TV presentation state.
- All ten implemented TV surfaces are addressable by the reducer.
- Continuity state carries destination, surface, title, active item, playback state, and a monotonic command revision.
- Duplicate and stale commands are rejected without mutating the presentation state.
- Paired-phone commands fail closed when pairing is absent, revoked, or expired.
- Pairing revocation is an explicit shared control intent.
- Network/cast transport remains deferred: no Socket, MediaRouter, Cast SDK, or screen mirroring is added by TVOV16.
