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

## StudyFlow Link Protocol — SFLP01-SFLP24 — COMPLETE

1. **SFLP01 Protocol baseline — COMPLETE** — protocol version, message envelope, workspace/device/
   entity/operation identities, capability vocabulary, request/response/events, compatibility rules,
   language-neutral schema, and golden vectors.
2. **SFLP02 Domain operation journal — COMPLETE** — canonical append-only operations, vector/entity
   revisions, deterministic replay, idempotent dedupe, checkpoints, and tombstones.
3. **SFLP03 Reconciliation engine — COMPLETE** — frontier exchange, bounded missing-operation batches,
   deterministic replay/conflict handling, duplicate-safe retry, and interrupted-sync recovery.
4. **SFLP04 Device identity and trust — COMPLETE** — persistent identity/trust, remembered peers,
   bounded six-digit/QR pairing, revocation, and authenticated reconnect proofs.
5. **SFLP05 Transport abstraction — COMPLETE** — platform-neutral connection/listener/discovery contracts,
   LAN TCP, WebSocket/relay adapters, endpoint parsing, and NSD/mDNS records.
6. **SFLP06 Multi-peer workspace topology — COMPLETE** — simultaneous peer registry, direct/learned/relay
   routes, bounded dedupe, loop prevention, and deterministic route selection.
7. **SFLP07 Offline queue/reconnection — COMPLETE** — persistent per-peer delivery queues, bounded retry,
   frontier catch-up, reconnect convergence, and interrupted-transfer recovery.
8. **SFLP08 Android integration — COMPLETE** — persistent generic Android peer, discovery, pairing, direct
   sync, reconnect, revoke, and forget without privileged hub semantics.
9. **SFLP09 Desktop integration — COMPLETE** — filesystem-persistent generic Desktop peer with discovery,
   direct pairing/sync, reconnect scheduling, and trust UI.
10. **SFLP10 Web integration — COMPLETE** — persistent browser identity, pairing/reconnect, IndexedDB-backed
    peer state, browser endpoints, frontier sync, and offline runtime cache.
11. **SFLP11 Android TV generalization — COMPLETE** — Android TV is a generic `android_tv` SFLP peer; the
    older phone/TV path is compatibility-only rather than the topology model.
12. **SFLP12 Web TV integration — COMPLETE** — Web TV is a generic execution/presentation SFLP peer with
    receiver pairing, projection, and target-gated control/input handling.
13. **SFLP13 Activity handoff — COMPLETE** — typed resumable handoff with expiry, one-time consumption,
    capability checks, and TV write-safety.
14. **SFLP14 Live session continuity — COMPLETE** — one canonical session, fenced epochs, monotonic events,
    optional observers/controllers, and exactly-once completion accounting.
15. **SFLP15 Universal presentation — COMPLETE** — typed source/sink projection generalized across capable
    clients while TV-class sinks reject write-capability projection offers.
16. **SFLP16 Universal remote control — COMPLETE** — lease-token and target-gated control across trusted
    capable peers without implicit repository/database write authority.
17. **SFLP17 Input delegation — COMPLETE** — capability-gated text/keyboard/input delegation across trusted
    peers, including TV presentation sinks.
18. **SFLP18 Notification and scheduling sync — COMPLETE** — targeted notification delivery plus schedule
    reminder synchronization with TV-class read-only enforcement.
19. **SFLP19 Media/material streaming handoff — COMPLETE** — material offers, chunk replay/dedupe/acknowledgement,
    and presentation-sink authority boundaries.
20. **SFLP20 Clipboard/share/import bridge — COMPLETE** — target-gated clipboard preview/apply and share/import
    offers with TV preview-only/write-rejection behavior.
21. **SFLP21 Conflict-review UX — COMPLETE** — conflict review/open/resolve/ack flows with capability-aware
    write authority.
22. **SFLP22 Account/backup handoff — COMPLETE** — account ownership handoff plus backup offer/preview/restore
    planning and acknowledgement with TV audit/preview boundaries.
23. **SFLP23 Release validation consolidation — COMPLETE** — consolidated ordered SFLP validation chain and
    release validation plan/result semantics.
24. **SFLP24 Final release seal — COMPLETE** — terminal SFLP01-SFLP23 validation gate and final-seal vector.

SFLP uses capability negotiation instead of hardcoded phone/TV roles. Representative capabilities include
`workspace.read`, `workspace.write`, `session.execute`, `session.present`, `device.control`,
`device.presentation_sink`, `text_input.provide`, `library.read`, `goal.complete`, and `question.answer`.

### Historical promise reconciliation

An older roadmap draft assigned **Presence** to SFLP18 and a **full-mesh interoperability seal** to SFLP19.
Those labels no longer describe the shipped SFLP18/SFLP19 implementation: SFLP18 is notification/scheduling
sync and SFLP19 is media/material streaming handoff. ROADMAP01 resolves that drift without renumbering shipped
work:

- optional peer/activity Presence remains a real unimplemented promise and moves to **PRESENCE01**; existing
  route health and `lastSeen` bookkeeping are transport-health signals, not user/activity presence semantics;
- the former SFLP19 full-mesh acceptance promise is owned by **PARITY04**, which is the canonical five-client
  interoperability seal after the remaining Desktop/TV/semantic work is complete;
- **SFLP24 remains the terminal SFLP milestone**. ROADMAP01 does not create SFLP25 or reinterpret validated
  SFLP18-SFLP24 behavior.

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

**DOV24 status: complete.** Desktop is no longer classified as a preview client in the current capability matrix.

## TV convergence — TVOV27-TVOV31

- **TVOV27** full native-TV material viewer: text pages, image zoom, PDF navigation and media playback. **Implemented.**
- **TVOV28** goal projection plus complete/pause/resume actions. **Implemented.**
- **TVOV29** Error Notebook reclassification and resolve/reopen parity. **Implemented.**
- **TVOV30** native TV search with D-pad and SFLP delegated text input. **Implemented.**
- **TVOV31** Android-TV/Web-TV semantic parity seal using common fixtures. **Implemented.**

Complex authoring, destructive storage operations, import configuration and password entry remain handoff
workflows on TV unless a later capability review explicitly promotes them.

## Semantic drift prevention

**WEB20** introduces language-neutral golden vectors consumed by Kotlin/TypeScript/Desktop implementations
for readiness, schedule allocation/rescheduling, question/mock scoring, review/SRS progression, study-time
accounting, goal progress, error state, portable serialization and SFLP conflict resolution.

## Presence — PRESENCE01

**PRESENCE01** remains planned. It adds optional live peer/activity presence independently of replication
correctness and transport health: explicit online/away/offline/stale semantics, bounded expiry, reconnect behavior,
multiple simultaneous client instances, current resumable activity hints, capability-aware visibility, and loop-free
propagation. Presence must never become a prerequisite for durable sync correctness.

Transport `StudyFlowPeerHealth`, route availability, and peer `lastSeen` timestamps do **not** satisfy this milestone.

## Final acceptance — PARITY04

PARITY04 is the canonical successor to the old draft's SFLP19 full-mesh promise. It starts only after
**DOV24**, **TVOV31**, **WEB20**, and **PRESENCE01** are complete. The final cross-platform seal requires all
of the following:

- create/edit data on one full client and observe the same state on all connected applicable peers;
- start on one client, continue on another, present to either TV implementation and finish exactly once;
- perform offline mutations on multiple peers and reconcile without lost or duplicated operations;
- pair Web TV or Android TV directly with Desktop/Web without an Android phone intermediary;
- connect all five clients simultaneously without operation loops;
- reject unsupported operations through capability negotiation rather than silent no-op behavior;
- preserve platform-specific UX while producing equivalent domain results.

## Machine-checkable remaining-work contract

`docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json` is the machine-readable companion to this roadmap.
ROADMAP01 validation fails if the ordered remaining tracks, prerequisite edges, terminal milestones, Presence
resolution, or final release-evidence dependency drift from this canonical roadmap.

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


### SFLP14/SFLP15 status

SFLP14 live session continuity — delivered. SFLP15 universal presentation projection — delivered.


## SFLP16 + SFLP17 — COMPLETE

SFLP16 control protocol and SFLP17 input delegation are complete. Control is lease-token gated and target-gated, input delegation is capability-gated, and TV-class peers remain presentation/control sinks without repository/database write authority.

## SFLP18 + SFLP19 — COMPLETE

SFLP18 notification/scheduling sync and SFLP19 media/material streaming handoff are implemented.
The mesh now supports targeted reminder/review/session notifications, schedule reminder changes,
read-only material offers, monotonic chunk replay, duplicate suppression, chunk acknowledgements,
and TV/Web-TV write-authority rejection.


## SFLP20 + SFLP21 — COMPLETE

SFLP20 clipboard/share/import bridge and SFLP21 conflict-review UX are implemented. The mesh now
supports target-gated `share.import_offer`, `share.import_ack`, `conflict.review_open`,
`conflict.resolve`, and `conflict.resolution_ack` messages. TV/Web-TV peers remain preview-only for
imports and cannot receive library import or workspace write authority.


## SFLP22 + SFLP23 — COMPLETE

SFLP22 account/backup handoff and SFLP23 release validation consolidation are implemented. The mesh
now supports `account.handoff_offer`, `account.handoff_accept`, `account.handoff_reject`,
`backup.bundle_offer`, `backup.restore_plan`, `backup.restore_ack`, `release.validation_plan`, and
`release.validation_result`. TV/Web-TV peers remain audit/preview surfaces and reject ownership or
write restore authority.

SFLP23 defines the consolidated final validation task chain. Apply this overlay with `--no-validate`
until the final seal run is requested.

## SFLP24 — FINAL RELEASE SEAL

SFLP24 is implemented as the final validation command for the SFLP connectivity series. The gate
adds `verifySflp24FinalReleaseSeal`, restores every SFLP verifier into `checkAll`, and records the
language-neutral `release.validation_final_seal` golden vector. This is the final validation command
for the SFLP01-SFLP23 chain; run it with validation enabled instead of `--no-validate`.
