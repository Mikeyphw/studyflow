# ATT01–ATT12 Attention roadmap promise audit

This audit reconciles the roadmap from the ATT10 handoff package with the post-ATT11 repository and the shipped overlay inventories. The audit was performed before ATT12 packaging; gaps found during the audit are included in ATT12 rather than deferred.

## Overlay inventory reviewed

| Overlay | Inventory entries | Result |
| --- | ---: | --- |
| ATT01 | 34 | present and represented in current tree |
| ATT02 | 40 | present and represented in current tree |
| ATT03 | 28 | present and represented in current tree |
| ATT04 | 22 | present and represented in current tree |
| ATT05 | 24 | present and represented in current tree |
| ATT06 | 26 | present; UI reachability gap repaired by ATT12 |
| ATT07 | 26 | present; delivery-edge target-enforcement gap repaired by ATT12 |
| ATT08 | 24 | present and represented in current tree |
| ATT09 | 27 | present and represented in current tree |
| ATT10 | 53 | present and represented in current tree |
| ATT11 | 45 | present and represented in current tree |

The handoff ATT01–ATT09 artifacts all contain Devtool artifact inventories. ATT10/ATT11 are the subsequently applied tar overlays and are audited as part of the reconstructed current tree.

## Promise reconciliation

| Roadmap item | Roadmap promise | Current result after ATT12 |
| --- | --- | --- |
| ATT01 | Shared Attention domain, durable ledger, lifecycle/occurrence identity, SFLP notification/media foundation | **PASS** — canonical shared ledger/domain remains the only durable Attention truth. |
| ATT02 | Android runtime, categories, native notification/actions, foreground/background truth | **PASS** — production Android coordinator/receivers and focused verifier retained. |
| ATT03 | Android widget suite, state projection, quick actions, ATT01/02 regression | **PASS** — five-mode widget suite and update/recovery contracts retained. |
| ATT04 | Desktop preferences/runtime/tray/settings with truthful unsupported fallback | **PASS** — process-owned runtime/tray preserved; matrix now states process-running limitation explicitly. |
| ATT05 | Web runtime, permission flow, foreground scheduler, SW push/click/close/drain, center, controls, optional Push, badge, ledger convergence, generated parity | **PASS** — source/runtime parity and release checks remain in final gate; closed-browser claim stays capability gated. |
| ATT06 | Native TV + Web TV foreground attention, D-pad actions, ATT01 ack, foreground-only truth, diagnostics, SW TV drain | **PASS after ATT12 reconciliation** — native TV now renders the model's bounded actions; Web TV now actually boots/installs/renders the foreground Attention runtime. |
| ATT07 | Shared single-owner/bounded-mirror router, capability scoring, ledger suppression, explicit target safety, TV truth, handoff model/adapters | **PASS after ATT12 reconciliation** — Android/Desktop/Web production delivery edges now require local membership in the computed owner/mirror target set. |
| ATT08 | Parent-owned compact model, privacy-safe copy, bounded compact actions, Android/Desktop/TV/Web bridges, Web compact center/SW marker | **PASS** — compact surfaces remain projections and never independent owners. |
| ATT09 | Shared calendar model, ICS VEVENT/VALARM, privacy modes, ATT01 projection, platform bridges, Web Tools UI/parity | **PASS** — user-initiated export contract retained. |
| ATT10 | Timezone/overnight quiet hours, focus profiles, per-category escalation, explicit outcomes, ATT07+ATT01 integration, controls/projections/tests | **PASS** — existing focused verifier green before ATT12; final gate keeps ATT10 predecessor validation. |
| ATT11 | Unified evidence, platform reliability diagnostics, outcome analytics, debug/export, route audit, tests/docs | **PASS** — deterministic ATT01 projection retained; Web reliability still requires actual Push subscription for background status. |
| ATT12 | Five-client integration pass, clean user docs/matrix, explicit validation list, full promise audit, release-seal verifier/final command | **PASS in this overlay**. |

## Gaps found and folded into ATT12

### 1. ATT07 explicit-target safety stopped at route computation

The router correctly filtered `targetDeviceIds`, but Android, Desktop, and Web delivery loops recorded the route and then could continue surfacing locally. ATT12 adds a final owner/mirror membership guard at all three production delivery edges. Non-targeted replicated entries stay scheduled and auditable.

### 2. Native Android TV exposed only Open in the Activity

The ATT06 domain model produced category-specific bounded actions, but `StudyFlowTvActivity` hard-coded `OPEN`. ATT12 renders `banner.actions` and applies each action through the ATT06 runtime. Move-to-phone is acknowledged as a companion continuation and is not converted into an unrelated local Open command.

### 3. Web TV Attention runtime was defined but not booted

`StudyFlowWebTvAttentionRuntime` and its bridge existed, but `main.tsx` deliberately skipped construction of the ATT05 runtime when TV mode was active. ATT12 boots the shared ledger/runtime for `web_tv`, restricts its delivery capability to foreground-banner/ledger-only, installs the TV bridge, and renders the banner in the TV React shell.

### 4. Capability matrix contained stale or malformed claims

The matrix called foreground Web and process-owned Desktop delivery “native reminder/background delivery”, marked TV Attention glance surfaces missing, contained a malformed ATT08 compact row, and had free-floating ATT06 rows outside a table. ATT12 corrects those statements without expanding capabilities.

### 5. Primary Devtool Attention list had duplicate ATT07/08/09 tasks

ATT12 removes the duplicated entries and registers ATT01 through ATT12 exactly once in roadmap order for the primary Android target.

## Intentionally unchanged boundaries

- ATT12 does not invent closed-TV background delivery.
- ATT12 does not claim normal Web has closed-browser alarms without real Push infrastructure.
- ATT12 does not turn Desktop into an OS daemon.
- ATT12 does not make compact companions independent owners.
- ATT12 does not add a second Attention database or replace SFLP/ATT01 identity semantics.
- The generic SFLP transport remains responsible for cross-device movement; ATT12 seals Attention ownership and surface behavior rather than inventing a parallel transport.
