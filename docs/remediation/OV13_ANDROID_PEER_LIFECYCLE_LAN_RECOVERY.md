# OV13 Android peer lifecycle & LAN discovery recovery evidence

OV13 closes the Android lifecycle findings by replacing process-owned, automatic SFLP startup with explicit user/permission-controlled lifecycle state and restartable discovery.

## Finding closure matrix

| Finding | Runtime behavior | Evidence |
|---|---|---|
| S5-01 | App startup calls `startIfUserEnabled()` only; raw sockets are not opened until user opt-in exists. | App source contract test and verifier. |
| S5-02 | Peer readiness is represented by persisted lifecycle state outside `Application` initialization. | `AndroidStudyFlowPeerLifecycleState`. |
| S5-05 | NSD start/stop/register failures keep desired state and retry. | `scheduleRestartAfterFailure`. |
| S5-06 | Android and TV NSD adapters serialize/queue `resolveService` and retry failures. | `pendingResolveQueue` in both adapters. |
| S5-07 | IPv6 link-local scope IDs remain in endpoint host strings. | Core endpoint resolver test. |
| S5-08 | QR hints contain only externally reachable endpoints; there is no loopback fallback. | `pairingOffer()` filter and source test. |
| S5-10 | `NEARBY_WIFI_DEVICES` is checked before SFLP TCP/WebSocket and LAN beacon startup. | App source contract test. |
| S5-11 | A failed sync folder configuration releases the persisted SAF grant. | `StudyFlowApp` cleanup branch. |
| DS2-07 | Restart re-adds NSD discovery to the registry after close. | App source contract test. |
| DS2-39 | Startup failure backoff and retry state are explicit and recoverable. | `StudyFlowPeerLifecycleRecovery` test. |
