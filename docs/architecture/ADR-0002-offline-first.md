# ADR-0002: Offline-first local authority

- Status: Accepted
- Date: 2026-07-30

## Decision

The local database and private file store are authoritative for core Study Plan operations. Network
services are optional enhancements and synchronize through explicit background work.

## Consequences

- Account creation is not required.
- Features must define network-unavailable states.
- Media metadata remains available when playback is not.
- Future cloud synchronization must reconcile local mutations rather than replace them.
