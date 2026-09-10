# StudyFlow cross-platform capability matrix

This document is the canonical current-state capability map for StudyFlow clients. Historical phase,
release, and overlay documents describe what was true when those milestones shipped; when they disagree
with this matrix, this matrix owns the current product-state description.

## Status vocabulary

- **FULL** — feature is implemented as a normal first-class workflow on this client.
- **LIMITED** — useful subset exists, but intentionally or currently lacks part of the full workflow.
- **READ_ONLY** — state is consumable but mutation is intentionally unavailable.
- **HANDOFF** — the client can surface the workflow but delegates authoring or high-input work.
- **PLATFORM_SPECIFIC** — behavior belongs to another platform rather than representing a parity gap.
- **NOT_APPLICABLE** — the capability does not make sense for this client role.
- **MISSING** — applicable capability is not implemented yet and remains roadmap work.

## Product maturity

| Client | Current identity | Product role |
| --- | --- | --- |
| Android | `1.0.0-rc1` | Full local-first workspace and Android-native reference client |
| Web | `1.0.0` | Full browser-applicable workspace |
| Desktop | `0.1.0` | Preview workspace; persistence and broad execution parity remain roadmap work |
| Android TV | `0.1.0-tv26` | Native 10-foot presentation/execution client |
| Web TV | Web `1.0.0` TV mode | Browser 10-foot presentation/execution client |

## Functional capability matrix

| Capability | Android | Web | Desktop | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Study Plan lifecycle | FULL | FULL | MISSING | HANDOFF | HANDOFF |
| Subjects and nested topics | FULL | FULL | MISSING | READ_ONLY | READ_ONLY |
| Schedule authoring | FULL | FULL | LIMITED | READ_ONLY | READ_ONLY |
| Adaptive rescheduling | FULL | FULL | MISSING | HANDOFF | HANDOFF |
| Study-session execution | FULL | FULL | MISSING | FULL | FULL |
| Questions and practice | FULL | FULL | LIMITED | FULL | FULL |
| Mock Exam Studio | FULL | FULL | MISSING | FULL | FULL |
| Reviews / SRS | FULL | FULL | MISSING | LIMITED | FULL |
| Flashcards | FULL | FULL | MISSING | LIMITED | FULL |
| Error Notebook | FULL | FULL | MISSING | LIMITED | FULL |
| Goals | FULL | FULL | MISSING | READ_ONLY | LIMITED |
| Progress / analytics | FULL | FULL | LIMITED | READ_ONLY | READ_ONLY |
| Library catalog | FULL | FULL | LIMITED | LIMITED | FULL |
| Full material viewing | FULL | FULL | LIMITED | LIMITED | FULL |
| Writing Workspace | FULL | FULL | MISSING | HANDOFF | HANDOFF |
| Global search | FULL | FULL | LIMITED | MISSING | FULL |
| Concurso Público workflows | FULL | FULL | MISSING | LIMITED | LIMITED |
| Programming / language content modes | FULL | FULL | MISSING | LIMITED | LIMITED |
| Import / export / portable backup | FULL | FULL | MISSING | HANDOFF | HANDOFF |
| Optional provider sync | FULL | PLATFORM_SPECIFIC | MISSING | NOT_APPLICABLE | NOT_APPLICABLE |
| English / pt-BR UI | FULL | FULL | MISSING | LIMITED | FULL |
| System / Light / Dark appearance | FULL | LIMITED | MISSING | PLATFORM_SPECIFIC | LIMITED |
| Android reminders / widgets | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |
| Termux exercise execution | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |

`LIMITED` on Desktop means the current DOV01-DOV07 preview exposes display/search/settings surfaces
backed primarily by in-memory repositories; it is not a claim of durable workspace parity.

## Current connectivity truth

StudyFlow now has the platform-neutral SFLP v1 envelope/capability contract, the SFLP02/SFLP03 canonical operation journal and reconciliation engine, the SFLP04/SFLP05 generic identity, trust, pairing, reconnect, transport, and
discovery foundation, the SFLP06/SFLP07 multi-peer topology plus durable offline-delivery/reconnect core, and the
first real product bindings in SFLP08/SFLP09. Android and Desktop now instantiate generic SFLP peers directly: each
owns stable identity/trust state, persistent journal/queue state, discovery endpoints, pairing, authenticated reconnect,
and frontier reconciliation. A direct Android↔Desktop link no longer depends on the legacy phone↔TV path.

The historical SFLP04/SFLP05 boundary said that **client bindings and multi-peer routing begin in SFLP06-SFLP12**;
SFLP06/SFLP07 completed the shared-core portion while the then-current statement was that universal client bindings
were **SFLP08-SFLP12 work**. SFLP08 and SFLP09 are now complete, while Web/TV bindings remain SFLP10-SFLP12.
StudyFlow therefore does **not yet** provide a universal cross-client synchronization mesh across all five clients. Current live connectivity is:

- Android ↔ Desktop: first-class SFLP discovery/pairing/trust/reconnect/workspace-operation synchronization.
- Android ↔ native Android TV: existing specialized TVOV pairing/control/presentation path until SFLP11 generalizes TV.
- Web TV: existing WTV browser receiver plus browser remote-control path until SFLP12.
- Normal Web: no universal SFLP peer binding yet; SFLP10 remains.
- Desktop no longer depends on Android as a connectivity hub and can pair directly with any compatible current/future
  SFLP peer exposed through the generic transport contracts.

The active SFLP roadmap continues removing the remaining special-case TV/browser assumptions.

## Target connectivity matrix

When SFLP19 is complete, every pair below must support protocol-compatible discovery/pairing and
workspace synchronization, subject to advertised capabilities:

| From / To | Android | Desktop | Web | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Android | — | FULL | FULL | FULL | FULL |
| Desktop | FULL | — | FULL | FULL | FULL |
| Web | FULL | FULL | — | FULL | FULL |
| Android TV | FULL | FULL | FULL | — | FULL |
| Web TV | FULL | FULL | FULL | FULL | — |

TV clients remain capability-limited clients, not second-class transport endpoints. A TV may reject
complex authoring operations while still discovering peers, synchronizing applicable state, handing
off activities, presenting material, receiving remote input, and contributing valid study operations.

## Intentional parity boundaries

Parity is semantic, not visual or API-level symmetry. The following differences are intentional:

- Android owns Android alarms, widgets, SAF/FileProvider behavior, Android background execution, and
  Termux-specific execution.
- Desktop may own filesystem, keyboard, tray, desktop process, and native notification adapters.
- Browser clients retain IndexedDB/browser sandbox and browser-compatible transport constraints.
- TV clients retain 10-foot layouts, D-pad focus, reduced authoring, and presentation-first UX.

Applicable domain results must still converge to equivalent StudyFlow state.


## SFLP10/SFLP11 status

- Web: generic SFLP peer implemented for browser and Web-TV modes with local persistence, manual/WebSocket endpoints, QR pairing import, offline runtime cache, and frontier-based sync helpers.
- Android TV: generic `android_tv` SFLP peer implemented with `:core:link` and TV-specific persistent identity/trust/journal/queue stores. The legacy phone/TV control path remains for compatibility but is no longer the SFLP topology model.
- Android is not a required hub for Web, Desktop, Android TV, or Web-TV SFLP synchronization.


### SFLP12/SFLP13 status

- Web TV now supports receiver QR/code connectivity, projection snapshots and remote D-pad/text input as a generic SFLP peer.
- Android, Desktop, Web, Android TV and Web TV can publish or accept typed activity handoffs when the target capability set is sufficient.
- TV-class clients reject write-capability handoffs; mutation truth remains in the journal/reconciliation pipeline.


## SFLP14/SFLP15 status

Live session continuity is protocol-backed across Android, Desktop, Web, Android TV and Web TV through fenced epochs,
monotonic events and exactly-once completion. Universal presentation projection is source/sink based; TV-class
receivers remain read/presentation/control clients and reject write-capability projection offers.


### SFLP16/SFLP17 control and input delegation

| Capability | Android | Desktop | Web | Web TV | Android TV |
| --- | --- | --- | --- | --- | --- |
| Leased control commands | Source/target | Source | Source/target | Target | Target |
| Input delegation | Text/touch | Keyboard/text | Keyboard/text/touch | D-pad/select/back | D-pad/select/back |
| TV write authority | No implicit grant | No implicit grant | No implicit grant | Rejected | Rejected |

## SFLP18/SFLP19 notification and material streaming capability rows

| Capability | Android | Desktop | Web | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Targeted notification delivery | Send/receive | Send/receive | Send/receive | Receive/read-only | Receive/read-only |
| Schedule reminder sync | Read/write | Read/write | Read/write | Read-only | Read-only |
| Material stream offer | Source/sink | Source/sink | Source/sink | Presentation sink only | Presentation sink only |
| Material chunk acknowledgement | Yes | Yes | Yes | Yes, no import | Yes, no import |
| Library import via media stream | Yes | Yes | Optional | No | No |
