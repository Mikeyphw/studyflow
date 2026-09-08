# TVOV03 pairing, control, and cast protocol contracts

TVOV03 freezes the shared protocol used by the Android mobile app and Android TV app.

## Contracts

- The protocol module is pure Kotlin and uses Kotlin serialization.
- The mobile app and TV app both depend on `:core:tvcontrol`.
- Pairing is explicit, short-lived, and revocable.
- Commands use StudyFlow intents: surface, action, target id, option id, timestamp, and user-presence requirement.
- Cast state describes StudyFlow presentation state, not raw screen mirroring.
- No raw database rows, Room entities, DAO types, file paths, or storage handles are part of the protocol.
- Study sessions, question sets, reviews, mock exams, progress, and schedule are castable surfaces.
- Settings and pairing internals are not castable presentation surfaces.
- No Cast SDK is introduced.
- No MediaRouter, AndroidX TV UI, Compose Runtime, phone feature-module fanout, `core:data`, or `core:database` is introduced to `:app-tv`.
