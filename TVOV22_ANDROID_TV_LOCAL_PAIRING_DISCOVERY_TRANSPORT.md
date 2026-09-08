# TVOV22 — Android TV local pairing, discovery, and transport

TVOV22 implements the previously deferred local wire between the Android mobile app and `:app-tv` while preserving the small TV dependency boundary.

## Implemented

- Added `:core:tvlink`, a small Android framework module shared by phone and TV.
- Added explicit Android NSD discovery using service type `_studyflow-tv._tcp.`.
- Added a one-request-per-connection local TCP transport with strict 4-byte length-prefixed JSON frames capped at 1 MiB.
- Added a 120-second random six-digit pairing offer, random nonce, ten-attempt cap, and `studyflow://tv/pair` handoff payload suitable for the later QR/controller UI.
- Pairing discovery exposes the active nonce and expiry but never the six-digit code; the user must read the code from the TV.
- Unauthenticated pairing-info/ping responses expose no StudyFlow continuity, destination, active item, or presentation state.
- Added 24-hour random session id/session secret credentials stored in private, non-backed-up preferences on both devices.
- Added local and remote revocation plus session-expiry invalidation.
- Added authenticated operations for commands, typed TVOV21 presentation snapshots, continuity reads, revoke, and ping.
- TV remote and paired-phone commands still converge on `StudyFlowTvControlStateReducer`; received presentation snapshots still converge on `StudyFlowTvPresentationInbox`.
- Added production phone `StudyFlowTvReceiverDiscovery` and `StudyFlowTvLocalTransportClient` ownership in `DefaultAppContainer` for TVOV23 to expose in UI.
- Added a TV receiver host to `StudyFlowTvActivity`; it advertises only while the activity is started and asks for local-network permission before pairing.
- Bumped `app-tv` to version code 22 / `0.1.0-tv22`.

## Still intentionally open

TVOV22 provides the real local link, but it does not pretend the Android phone controller UI exists. TVOV23 is next and must make receiver discovery, code entry, pairing status, remote controls, cast entry points and revoke usable from the phone application. TVOV24 then completes live cast/state synchronization behavior on top of this transport.
