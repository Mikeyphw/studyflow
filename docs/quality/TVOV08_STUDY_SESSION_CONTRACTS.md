# TVOV08 Study session contracts

- The Study Session surface must expose a real state machine for ready, running, paused, and completed states.
- The session must support start, pause, resume, next segment, complete, and return-to-Today actions.
- The TV remote and paired-phone control path must use the same shared command protocol.
- Play/Pause remote-key handling must operate on the Study Session surface.
- cast state must be emitted as StudyFlow presentation state, not screen mirroring or database payloads.
- The TV app must preserve the dependency budget: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module fanout.
