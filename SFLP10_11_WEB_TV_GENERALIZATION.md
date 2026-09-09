# SFLP10 + SFLP11 — Web Peer and Android TV Generalization

This overlay makes the browser and Android TV surfaces first-class SFLP peers.

## SFLP10 Web integration

- Web starts a persistent `StudyFlowWebLinkPeer` during boot.
- Normal browser mode advertises `web` capabilities; TV-browser mode advertises `web_tv` capabilities.
- Web persists identity, trust, endpoints, journal, frontiers, and outgoing queue in local browser storage under `studyflow.sflp.web.peer.v1`.
- Web supports manual endpoints and `ws` / `wss` peer transport through `StudyFlowWebSocketTransportAdapter`.
- Web supports QR/code pairing import, trusted-peer revocation, forgetting peers, idempotent operation ingest, frontier calculation, missing-operation lookup, and acknowledgement-based queue cleanup.
- The service worker offline shell caches `runtime/sflp_link.js`, so the peer state/control layer remains available after installation.
- Web remains local-first; no relay or backend is required.

## SFLP11 Android TV generalization

- Android TV now has a generic `StudyFlowTvSflpPeer` using `StudyFlowLinkClientKind.ANDROID_TV` and the shared `:core:link` runtime.
- The old TVOV22 phone/TV local transport remains as a compatibility adapter for existing remote-control behavior.
- Android TV is no longer modeled as a phone-owned spoke in the SFLP layer.
- TV capabilities are presentation/read/sync/control capabilities only: no plan-write or repository/database ownership is added to the TV APK.
- Android TV persists identity, trust, endpoints, journals, and queues in a TV-specific SharedPreferences store.
- Android TV can pair and sync with Android, Desktop, Web, and Web TV peers through the same trust and reconciliation contracts used by previous SFLP phases.

## Acceptance

SFLP10 and SFLP11 are accepted only when predecessor SFLP01-SFLP09 contracts still pass, the Web runtime is committed and offline cached, the Android TV app has `:core:link` without importing phone feature/database modules, and Android TV keeps its legacy TVOV26 presentation behavior while adding generic peer capability.
