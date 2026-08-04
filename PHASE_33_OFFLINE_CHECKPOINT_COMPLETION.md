# Phase 33: Offline-first checkpoint completion

Phase 33 resolves the former sync foundation as an offline-first local checkpoint feature. StudyFlow now focuses on local backup readiness, restore-safe change summaries, and device-only checkpoint state.

Delivered:

- User-facing copy now describes a local checkpoint that stays on this device.
- The local checkpoint screen talks about device-local checkpoint evidence and local changes waiting for backup.
- The sync model no longer contains a placeholder provider or transfer counters.
- State now exposes `checkpointReady`, `localBackupAvailable`, and `pendingLocalChangeCount`.
- Historical persisted status names are still decoded so existing installs do not lose their local checkpoint state.
- Runtime smoke and diagnostic probe copy uses Local checkpoint language.
- Phase 33 verifier rejects user-facing online-sync copy in Settings resources.

Intentional boundary:

- StudyFlow remains offline-first for this track.
- Online synchronization, sign-in, WebDAV/Drive/Firebase, and shared-device conflict review are out of scope until a future major feature explicitly chooses them.
