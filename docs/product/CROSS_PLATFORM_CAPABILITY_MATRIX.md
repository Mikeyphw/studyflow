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
| Desktop | `1.0.0` | Full applicable desktop workspace with JVM-native platform integrations |
| Android TV | `0.1.0-tv26` | Native 10-foot presentation/execution client |
| Web TV | Web `1.0.0` TV mode | Browser 10-foot presentation/execution client |

## Functional capability matrix

| Capability | Android | Web | Desktop | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Study Plan lifecycle | FULL | FULL | FULL | HANDOFF | HANDOFF |
| Subjects and nested topics | FULL | FULL | FULL | READ_ONLY | READ_ONLY |
| Schedule authoring | FULL | FULL | FULL | READ_ONLY | READ_ONLY |
| Adaptive rescheduling | FULL | FULL | FULL | HANDOFF | HANDOFF |
| Study-session execution | FULL | FULL | FULL | FULL | FULL |
| Questions and practice | FULL | FULL | FULL | FULL | FULL |
| Mock Exam Studio | FULL | FULL | FULL | FULL | FULL |
| Reviews / SRS | FULL | FULL | FULL | LIMITED | FULL |
| Flashcards | FULL | FULL | FULL | LIMITED | FULL |
| Error Notebook | FULL | FULL | FULL | LIMITED | FULL |
| Goals | FULL | FULL | FULL | READ_ONLY | LIMITED |
| Progress / analytics | FULL | FULL | FULL | READ_ONLY | READ_ONLY |
| Library catalog | FULL | FULL | FULL | LIMITED | FULL |
| Full material viewing | FULL | FULL | FULL | LIMITED | FULL |
| Writing Workspace | FULL | FULL | FULL | HANDOFF | HANDOFF |
| Global search | FULL | FULL | FULL | MISSING | FULL |
| Concurso Público workflows | FULL | FULL | FULL | LIMITED | LIMITED |
| Programming / language content modes | FULL | FULL | FULL | LIMITED | LIMITED |
| Import / export / portable backup | FULL | FULL | FULL | HANDOFF | HANDOFF |
| Optional provider sync | FULL | PLATFORM_SPECIFIC | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE |
| English / pt-BR UI | FULL | FULL | FULL | LIMITED | FULL |
| System / Light / Dark appearance | FULL | LIMITED | FULL | PLATFORM_SPECIFIC | LIMITED |
| Android reminders / widgets | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |
| Termux exercise execution | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |

Desktop DOV08-DOV24 now provide the full applicable Desktop workspace: durable domain state, authoring and
execution workflows, Writing Workspace, programming/language modes, Concurso Público, shared-engine analytics,
Goals, portable structure packages, encrypted full-workspace backup/restore, migration, English/pt-BR localization,
System/Light/Dark appearance, native file/browser/notification/deep-link integration, full material handoff to system
viewers, and global search. Android provider adapters remain platform-specific rather than a Desktop parity gap.

## Current connectivity truth

StudyFlow has completed the SFLP01-SFLP24 protocol series. Android, Desktop, normal Web, Android TV, and
Web TV now all have generic SFLP peer bindings with capability negotiation. The shared core covers identity/trust,
discovery and transport abstractions, multi-peer topology, durable queue/reconnect, operation reconciliation, activity
handoff, live-session continuity, presentation/control/input delegation, notification/schedule sync, material streaming,
share/import/conflict review, account/backup handoff, and the consolidated SFLP24 validation seal.

Current connectivity implementation truth:

- Android and Desktop own persistent generic peers and can pair/synchronize directly.
- Browser Web and Web TV own generic SFLP peer state and participate through browser-compatible endpoints.
- Native Android TV owns an `android_tv` SFLP peer; the specialized phone/TV adapter remains compatibility-only.
- Android is not a required topology hub for Desktop, Web, Android TV, or Web TV.
- TV-class clients remain capability-limited presentation/execution peers and reject unsupported write/import/restore
  authority rather than silently accepting it.

SFLP24 proves the SFLP protocol/behavior chain, but it is **not** the final five-client product-parity acceptance.
DOV08-DOV24 and TVOV27-TVOV29 are complete. TVOV30-TVOV31, WEB20, PRESENCE01, and PARITY04 remain the ordered cross-platform closure work.

### Protocol connectivity matrix

Generic SFLP bindings exist for every client pair below, subject to advertised capabilities and available platform
transport adapters. **PARITY04 is still required to prove pairwise and simultaneous five-peer behavior end-to-end.**

| From / To | Android | Desktop | Web | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Android | — | SUPPORTED | SUPPORTED | SUPPORTED | SUPPORTED |
| Desktop | SUPPORTED | — | SUPPORTED | SUPPORTED | SUPPORTED |
| Web | SUPPORTED | SUPPORTED | — | SUPPORTED | SUPPORTED |
| Android TV | SUPPORTED | SUPPORTED | SUPPORTED | — | SUPPORTED |
| Web TV | SUPPORTED | SUPPORTED | SUPPORTED | SUPPORTED | — |

TV clients are capability-limited clients, not second-class transport endpoints. A TV may reject complex authoring
operations while still discovering peers, synchronizing applicable state, handing off activities, presenting material,
receiving remote input, and contributing operations that its advertised capability set permits.

### Presence truth

StudyFlow currently has transport peer-health and `lastSeen` bookkeeping, but no canonical optional user/activity
presence model. Presence therefore remains **MISSING** as **PRESENCE01** and must stay independent of replication
correctness. The old draft label “SFLP18 Presence” is superseded; shipped SFLP18 is notification/scheduling sync.

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

## SFLP20/SFLP21 share/import and conflict review capability rows

| Capability | Android | Desktop | Web | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Clipboard preview | Yes | Yes | Yes | Yes | Yes |
| Clipboard apply | Yes | Yes | Yes | No | No |
| Share import | Yes | Yes | Yes | No | No |
| Import preview | Yes | Yes | Yes | Yes | Yes |
| Conflict review | Open/resolve | Open/resolve | Open/resolve | Review/display only | Review/display only |
| TV write/import authority | Not applicable | Not applicable | Not applicable | Rejected | Rejected |

## SFLP22/SFLP23 account backup and validation capability rows

| Capability | Android | Desktop | Web | Android TV | Web TV |
| --- | --- | --- | --- | --- | --- |
| Account handoff ownership | Yes | Yes | Yes | No | No |
| Account handoff audit | Yes | Yes | Yes | Yes | Yes |
| Backup export | Yes | Yes | Yes | Yes | No |
| Backup restore | Yes | Yes | Yes | No | No |
| Backup preview/verify | Yes | Yes | Yes | Yes | Yes |
| Final validation plan visibility | Yes | Yes | Yes | Yes | Yes |

## SFLP24 final validation capability rows

| Capability | Android | Desktop | Android TV | Web | Web TV |
| --- | --- | --- | --- | --- | --- |
| Final SFLP validation gate | Required | Required | Required | Runtime-audited | Runtime-audited |
| SFLP01-SFLP23 predecessor chain | Required | Required | Required | Required | Required |
| TV write authority during final restore checks | Rejected | N/A | Rejected | N/A | Rejected |
