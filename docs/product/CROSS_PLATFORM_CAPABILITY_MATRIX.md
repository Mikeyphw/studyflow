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

StudyFlow does **not yet** provide a universal cross-client synchronization mesh. Current connectivity
work is specialized:

- Android and native Android TV have the TVOV local pairing/control/presentation path.
- Web TV has its WTV browser receiver plus browser remote-control path.
- Desktop has no first-class peer link yet.
- Normal Web has no universal peer synchronization protocol yet.
- Android is therefore still implicitly privileged by the native-TV path today.

The active SFLP roadmap removes these special-case assumptions.

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
