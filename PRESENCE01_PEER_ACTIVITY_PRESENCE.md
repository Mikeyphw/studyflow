# PRESENCE01 — Peer/activity presence

PRESENCE01 closes the historical Presence promise without changing the terminal SFLP24 numbering.

## Contract

- Presence is **ephemeral** and never canonical workspace data.
- Availability is explicit: `online`, `away`, or `offline`; a non-offline record becomes `stale` after 45 seconds and expires after 180 seconds by default.
- Each process instance owns an `instanceId` and reconnect `sessionId`; a newer session supersedes older presence for the same device/instance while multiple instances can coexist.
- Optional activity hints reuse typed StudyFlow activity IDs/kinds/continuity and required capabilities. An incapable viewer may see the peer while the activity hint is redacted; record-level visibility capabilities may hide the entire record.
- Authenticated peers exchange bounded batches only after normal durable reconciliation. Presence exchange is wrapped as best-effort/non-fatal so replication correctness never depends on it.
- Relayed records reuse `StudyFlowRouteTrace`; already-visited peers and hop-limit exhaustion are rejected to prevent presence loops.
- Revoking or forgetting a peer removes its ephemeral presence immediately.

## Client surfaces

Android and Android TV expose presence through their native SFLP peer wrappers. Desktop exposes it through `DesktopStudyFlowLinkService` and Settings. Web and Web TV share `StudyFlowWebPresenceRuntime`; presence is intentionally absent from the persisted `SflpWebPeerSnapshot`.

## Validation

`verifyPresence01PeerActivityPresence` checks the schema, runtime wiring, all five clients, ephemeral boundary, roadmap closure, and focused tests. `verifyPresence01TerminalSeal` adds core/link integration tests, Desktop/Android/TV compile + unit tests, and the complete Web lint/test/release surface.

After PRESENCE01, **PARITY04 is complete**; REL-FINAL evidence execution is next.

## Terminal reliability hardening

The browser-TV LAN relay acceptance harness waits for actual HTTP readiness, retries fresh ephemeral ports after early bind/startup failures, and captures child-process diagnostics. This prevents loaded parallel Gradle runs from turning slow Node startup into a false PRESENCE01 regression. `verifyPresence01LanRelayStability` is part of the terminal seal.
