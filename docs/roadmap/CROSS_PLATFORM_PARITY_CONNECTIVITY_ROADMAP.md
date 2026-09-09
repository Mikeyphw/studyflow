# StudyFlow cross-platform parity and connectivity roadmap

## Goal

Turn StudyFlow from an Android-centered product with companion surfaces into one workspace ecosystem:
Android, Desktop, Web, Android TV, and Web TV must share domain semantics and participate in a common
peer protocol. Android/Web remain the current full-workspace reference implementations; Desktop is the
major functional-parity track; TV clients preserve intentionally narrower 10-foot authoring boundaries.

## Baseline — PARITY01-PARITY03

- **PARITY01** — establish `docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md` as current product truth.
- **PARITY02** — reconcile stale README/scope/release/roadmap language with shipped Web, Desktop, TV,
  and optional sync tracks.
- **PARITY03** — restore the existing OV20 source-quality/integration seal without weakening validators.

## StudyFlow Link Protocol — SFLP01-SFLP19

1. **SFLP01 Protocol baseline — COMPLETE** — protocol version, message envelope, workspace/device/
   entity/operation identities, capability vocabulary, request/response/events, compatibility rules,
   language-neutral schema, and golden vectors. No transport or replication is claimed yet.
2. **SFLP02 Domain operation journal — COMPLETE** — canonical append-only operations, per-origin vector
   revisions, entity revisions, deterministic replay, idempotent dedupe, snapshot checkpoints, and tombstones.
3. **SFLP03 Reconciliation engine — COMPLETE** — typed frontier exchange, bounded missing-operation batches,
   deterministic replay/conflict handlers, duplicate-safe retry, and interrupted-sync recovery from receiver truth.
4. **SFLP04 Device identity and trust — COMPLETE** — persistent identity/trust ports, generic remembered peers,
   six-digit + QR pairing, bounded offers/attempts, revocation, and one-time authenticated reconnect proofs.
5. **SFLP05 Transport abstraction — COMPLETE** — platform-neutral connection/listener/discovery contracts, real
   length-prefixed LAN TCP, adapter-backed WebSocket/relay schemes, manual endpoint parsing, and NSD/mDNS records.
6. **SFLP06 Multi-peer workspace topology — COMPLETE** — simultaneous peer registry, health-aware direct/
   learned/relay routes, source-preserving route traces, bounded message dedupe, loop prevention, and deterministic
   route selection.
7. **SFLP07 Offline queue/reconnection — COMPLETE** — persistent idempotent per-peer delivery queue, bounded
   retry backoff, stale-peer catch-up from vector frontiers, bidirectional reconnect convergence, and interrupted
   transfer recovery from receiver truth.
8. **SFLP08 Android integration — COMPLETE** — Android owns a persistent generic SFLP peer with private identity/
   trust/journal/queue state, NSD + LAN/manual discovery, QR pairing, authenticated direct sync, and revoke/forget;
   the Android phone is not a privileged SFLP hub. Android becomes a generic SFLP peer rather than the TV hub.
9. **SFLP09 Desktop integration — COMPLETE** — Desktop owns the same generic peer runtime with filesystem-persistent
   identity/trust/journal/queue/endpoints, LAN/manual discovery, direct pairing/sync, reconnect scheduling, and trust UI.
10. **SFLP10 Web integration** — persistent browser identity, pair/reconnect, IndexedDB-backed peer state.
11. **SFLP11 Android TV generalization** — accept any compatible SFLP peer, not just Android phone.
12. **SFLP12 Web TV integration** — Web TV becomes a true SFLP execution/presentation peer.
13. **SFLP13 Activity handoff** — continue resumable activities on another capable peer.
14. **SFLP14 Live session continuity** — one canonical session, one active executor, optional observers/
    controllers, duplicate-proof time/completion accounting.
15. **SFLP15 Universal presentation** — generalize typed TV projection to any presentation source/sink.
16. **SFLP16 Universal remote control** — any trusted control-capable peer can control a presentation sink.
17. **SFLP17 Input delegation** — request keyboard/text input from another trusted peer.
18. **SFLP18 Presence** — optional live peer/activity status independent of replication correctness.
19. **SFLP19 Full-mesh interoperability seal** — pairwise Android/Desktop/Web/Android-TV/Web-TV tests for
    pairing, reconnect, sync, offline edits, dedupe, handoff, presentation, control and version refusal.

SFLP uses capability negotiation instead of hardcoded phone/TV roles. Representative capabilities include
`workspace.read`, `workspace.write`, `session.execute`, `session.present`, `device.control`,
`device.presentation_sink`, `text_input.provide`, `library.read`, `goal.complete`, and `question.answer`.

## Desktop parity — DOV08-DOV24

- **DOV08** persistent desktop workspace.
- **DOV09** real Plan/subject/topic lifecycle.
- **DOV10** planner authoring and adaptive schedule workflows.
- **DOV11** study-session runtime.
- **DOV12** questions/practice execution.
- **DOV13** Mock Exam Studio.
- **DOV14** reviews, SRS, flashcards and Error Notebook.
- **DOV15** real library/files and material viewers.
- **DOV16** Writing Workspace.
- **DOV17** content modes with desktop-appropriate execution backends.
- **DOV18** Concurso Público parity.
- **DOV19** readiness, coaching, cleanup, reports, runbooks and sprints.
- **DOV20** portable export/import, encrypted backup/restore and migration.
- **DOV21** real English/pt-BR localization.
- **DOV22** real System/Light/Dark appearance.
- **DOV23** desktop-native file picker/filesystem/notifications/deep links.
- **DOV24** full applicable Desktop parity seal; preview label may be removed only after this gate.

## TV convergence — TVOV27-TVOV31

- **TVOV27** full native-TV material viewer: text pages, image zoom, PDF navigation and media playback.
- **TVOV28** goal projection plus complete/pause/resume actions.
- **TVOV29** Error Notebook reclassification and resolve/reopen parity.
- **TVOV30** native TV search with D-pad and SFLP delegated text input.
- **TVOV31** Android-TV/Web-TV semantic parity seal using common fixtures.

Complex authoring, destructive storage operations, import configuration and password entry remain handoff
workflows on TV unless a later capability review explicitly promotes them.

## Semantic drift prevention

**WEB20** introduces language-neutral golden vectors consumed by Kotlin/TypeScript/Desktop implementations
for readiness, schedule allocation/rescheduling, question/mock scoring, review/SRS progression, study-time
accounting, goal progress, error state, portable serialization and SFLP conflict resolution.

## Final acceptance — PARITY04

The final cross-platform seal requires all of the following:

- create/edit data on one full client and observe the same state on all connected applicable peers;
- start on one client, continue on another, present to either TV implementation and finish exactly once;
- perform offline mutations on multiple peers and reconcile without lost or duplicated operations;
- pair Web TV or Android TV directly with Desktop/Web without an Android phone intermediary;
- connect all five clients simultaneously without operation loops;
- reject unsupported operations through capability negotiation rather than silent no-op behavior;
- preserve platform-specific UX while producing equivalent domain results.

## Validation architecture

Keep target validation independent:

1. shared-domain validation;
2. Android validation;
3. Desktop validation;
4. Web validation;
5. Android-TV validation;
6. connectivity validation;
7. cross-platform parity validation;
8. explicit full-repository release validation.

A target-specific overlay should not fail solely because an unrelated client has a source-quality problem
unless that overlay explicitly invokes the repository-wide release gate.

- SFLP10 | Web integration — delivered: Browser and Web-TV modes now boot persistent SFLP peers with local storage, manual/WebSocket endpoints, QR pairing import, offline cached runtime, frontier sync, queue acknowledgement, revoke, and forget.
- SFLP11 | Android TV generalization — delivered: Android TV now participates as an `android_tv` SFLP peer over `:core:link`; the existing phone/TV adapter remains compatibility-only, and TV stays a presentation/read/sync peer rather than a data-owner hub.


### SFLP12/SFLP13 delivery status

- SFLP12 | Web TV connectivity — delivered: Web TV advertises as `web_tv`, publishes receiver QR/code payloads, accepts projection snapshots, gates remote input by target device and remains a mesh peer rather than a phone-owned spoke.
- SFLP13 | Activity handoff — delivered: typed handoff offers, accepts, resume events, expiry, one-time consumption, capability checks and TV write-safety are implemented across shared core, Android, Desktop, Android TV and Web/Web TV.
