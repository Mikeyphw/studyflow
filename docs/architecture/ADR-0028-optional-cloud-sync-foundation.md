# ADR-0028: Optional Cloud-Sync Foundation

## Decision

StudyFlow adds only a local cloud-sync foundation in Phase 24. The app records local readiness, change tokens,
pending upload units, device label, and conflict policy in DataStore while keeping Room as the source of truth.

## Rationale

The roadmap explicitly defers provider, identity, and conflict policy selection. Adding Firebase, Drive, WebDAV,
or a custom server now would create misleading security and data-ownership commitments. A local foundation keeps
the UI truthful and prepares a seam for future sync code.

## Consequences

- No network permission or cloud dependency is added.
- No account identity, OAuth token, or server endpoint is stored.
- Local checkpoints can become stale and must be refreshed before a real provider integration.
- Future sync overlays must implement provider selection, authenticated identity, remote manifests, and conflict review.
