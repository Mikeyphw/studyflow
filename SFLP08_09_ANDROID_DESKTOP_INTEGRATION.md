# SFLP08/SFLP09 — Android and Desktop peer integration

SFLP08 and SFLP09 bind the shared StudyFlow Link Protocol stack to the Android and desktop products. The phone is
no longer modeled as the mandatory connectivity hub: Android and Desktop both instantiate the same generic
`StudyFlowPeerRuntime`, advertise their own capabilities, persist their own trust/journal/queue state, and can pair
and reconcile directly over SFLP TCP.

## SFLP08 — Android generic peer

Delivered:

- `:app` depends on JVM-safe `:core:link` and owns `AndroidStudyFlowLinkPeer` in the application container.
- app initialization starts the peer as a recoverable optional subsystem; local/offline StudyFlow remains usable if
  networking is unavailable.
- stable device identity, trust records, endpoint records, operation journal, checkpoints, and outgoing delivery queue
  persist in private Android `SharedPreferences` through the shared SFLP persistence codec.
- Android advertises/discovers `_studyflow._tcp.` with NSD and also participates in the generic LAN beacon discovery
  fallback used by desktop peers.
- manual endpoints, QR pairing, remembered trust, revoke/forget, and authenticated frontier reconciliation are exposed
  by the Android binding.
- Android advertises capabilities as an Android client, not as a special phone/controller role. Existing TVOV phone/TV
  code remains a legacy compatibility lane until SFLP11 generalizes native TV.

## SFLP09 — Desktop generic peer

Delivered:

- `:desktopApp` now explicitly permits and depends on the JVM-safe `:core:link` boundary.
- the production desktop composition root owns a `DesktopStudyFlowLinkService`; the ordinary test/embed bootstrap
  remains side-effect free.
- stable identity, trust, endpoints, journals/checkpoints, and offline queues persist atomically below
  `~/.studyflow/link/v1/` by default.
- desktop listens for direct SFLP TCP peers, advertises/discovers generic LAN beacons, supports manual endpoints, and
  schedules reconnect attempts for registered workspaces.
- Settings exposes StudyFlow Link device status, pairing offer/code/QR payload, QR pairing, manual endpoint entry,
  trusted/discovered peers, sync, revoke, forget, and pending offline-operation count.
- desktop capabilities are negotiated through the shared SFLP capability set; Android modules and Android persistence
  are still forbidden dependencies.

## Direct Android ↔ Desktop proof

The shared JVM integration test creates one `ANDROID` peer and one `DESKTOP` peer, runs two real TCP listeners, pairs
through the generic QR path, authenticates using SFLP04 reconnect proof, creates independent offline mutations on both
sides, and performs SFLP03 bidirectional reconciliation until both journals contain exactly the same operations. A
second test proves a revoked peer cannot establish an authenticated sync session.

The peer-RPC golden vectors are language-neutral fixtures under `protocol/sflp/v1/` and cover generic reconnect and
sync-apply envelopes. They intentionally contain no phone/TV role names.

## Boundary after this overlay

Android and Desktop now have a first-class direct SFLP link. Web, Android TV, and Web TV are still awaiting their
SFLP10-SFLP12 bindings. Full domain projection/handoff/presentation/control remain later roadmap phases; this overlay
establishes the durable authenticated replication transport used by those phases.
