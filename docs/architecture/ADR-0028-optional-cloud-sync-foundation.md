# ADR-0028: Local checkpoint foundation

## Decision

StudyFlow keeps the Phase 24/33 local checkpoint foundation as the local-first base for optional cloud sync. The app records local readiness, change tokens, local change counts, device label, restore conflict policy, and off-by-default optional sync settings in DataStore while keeping Room as the source of truth.

## Rationale

The product direction is now local-first with optional cloud sync. A local checkpoint gives users an honest backup-readiness surface immediately, while provider-backed upload/download/merge behavior remains explicit and opt-in.

## Consequences

- No network permission is required by the checkpoint foundation.
- No account identity, OAuth token, or online endpoint is required by the checkpoint foundation.
- Fresh installs default to sync off, provider none, automatic sync off, Wi-Fi-only on, charging-only off, and remote-backup encryption on.
- Local checkpoints can become stale and should be refreshed before export/restore decisions.
- Future provider-backed synchronization must remain explicit and optional, not hidden behavior inside the local checkpoint flow.
