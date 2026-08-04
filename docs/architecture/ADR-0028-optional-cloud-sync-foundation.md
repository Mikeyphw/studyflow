# ADR-0028: Offline checkpoint foundation

## Decision

StudyFlow resolves the former optional sync foundation as an offline-first local checkpoint. The app records local readiness, change tokens, local change counts, device label, and restore conflict policy in DataStore while keeping Room as the source of truth.

## Rationale

The current product focus is offline. Adding online synchronization would create misleading security and data-ownership commitments. A local checkpoint gives users an honest backup-readiness surface without implying data transfer or shared-device behavior.

## Consequences

- No network permission or online dependency is added.
- No account identity, OAuth token, or online endpoint is stored.
- Local checkpoints can become stale and should be refreshed before export/restore decisions.
- Future online synchronization must be designed as a separate major feature, not as hidden behavior inside the local checkpoint flow.
