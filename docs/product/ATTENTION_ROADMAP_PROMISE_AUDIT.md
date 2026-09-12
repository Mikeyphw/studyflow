# ATT01–ATT12 Attention roadmap promise audit

This audit reconciles the roadmap from the ATT10 handoff package with the current repository and shipped overlay inventories. A post-ATT12 source-graph audit found additional production-reachability gaps that the original ATT12 verifier did not detect. Those gaps are closed by the post-seal completion hotfix and are recorded here instead of preserving the earlier false-green result.

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
| ATT06 | Native TV + Web TV foreground attention, D-pad actions, ATT01 ack, foreground-only truth, diagnostics, SW TV drain | **POST-SEAL PASS** — native TV now receives authenticated SFLP notification deliveries through its live ATT06 runtime and renders bounded actions; Web TV keeps the foreground-only runtime. |
| ATT07 | Shared single-owner/bounded-mirror router, capability scoring, ledger suppression, explicit target safety, TV truth, handoff model/adapters | **POST-SEAL PASS** — Android/Desktop/Web evaluate trusted/presence topology with canonical SFLP identities; Android/Desktop dispatch selected remote targets over authenticated notification peer RPC; Web provides targeted ingest/routing but does not claim a direct browser notification-RPC sender; explicit local target guards remain. The handoff object is the canonical request model, not a second transport. |
| ATT08 | Parent-owned compact model, privacy-safe copy, bounded compact actions, Android/Desktop/TV/Web bridges, Web compact center/SW marker | **POST-SEAL PASS** — Android Quick Settings/notification companion projection, Desktop tray projection, native-TV foreground compact overlay and Web compact surface consume the shared bridge model; Desktop advertises the mini surface as `WIDGET_OR_BADGE`, and native remote delivery preserves the selected compact route. |
| ATT09 | Shared calendar model, ICS VEVENT/VALARM, privacy modes, ATT01 projection, platform bridges, Web Tools UI/parity | **POST-SEAL PASS** — Android has a user-initiated calendar share flow, Desktop saves `.ics`, Android TV previews/continues elsewhere, and Web keeps download/copy. |
| ATT10 | Timezone/overnight quiet hours, focus profiles, per-category escalation, explicit outcomes, ATT07+ATT01 integration, controls/projections/tests | **POST-SEAL PASS** — cross-device outcomes receive real ATT07 topology and the selected route is transported to native remote targets, preserving `COMPACT_ONLY`/silent semantics; Android permission loss falls back to compact capability instead of selecting an unusable native surface. Native TV consumes its intentional local `Minimal/TV` read-only projection and Web TV visibly surfaces its browser profile/outcome projection. |
| ATT11 | Unified evidence, platform reliability diagnostics, outcome analytics, debug/export, route audit, tests/docs | **POST-SEAL PASS** — Android/Desktop/Web reliability sees cross-device topology; native TV consumes its read-only reliability projection; Web still requires actual Push for background status. |
| ATT12 | Five-client integration pass, clean user docs/matrix, explicit validation list, full promise audit, release-seal verifier/final command | **POST-SEAL PASS** — the verifier is hardened to reject the production-reachability gaps and capability overclaims that escaped the original seal; the terminal gate now includes the complete Web regression suite. |

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
- The generic SFLP transport remains responsible for cross-device movement; ATT12 seals Attention ownership and surface behavior rather than inventing a parallel transport. In this repository Android/Desktop expose native notification peer-RPC send/receive, while Web/Web TV expose targeted ingest/routing and must not be described as direct notification-RPC senders.

## Post-seal gaps found by the second source-graph audit

The original ATT12 result was not sufficient proof of full roadmap delivery. The post-seal audit specifically found and fixed: native-TV notification ingress, local-only ATT07 candidate topology, synthetic Attention device identities, orphan Android/Desktop/TV ATT08 bridges, orphan Android/Desktop/TV ATT09 bridges, and unsurfaced native-TV ATT10/ATT11 projections. A second semantic pass then caught and closed native Android/Desktop inbound-handler gaps, local-mirror suppression after remote-owner success, an Android `COMPACT_ONLY` regression that still posted a quiet system notification, same-millisecond SFLP delivery-sequence collisions, text-only Android calendar sharing, fabricated Android-TV calendar preview times, and the legacy Android ATT01 migration workspace formula. The terminal verifier now checks these failure modes directly instead of accepting bridge/token existence as proof.


## Independent roadmap-parity recheck

A later independent recheck found two truth/coverage issues in the first post-seal artifact itself: the capability matrix still labelled browser notification envelopes as `Send/receive`, and the triple-check report recorded only the focused 283-test subset instead of the complete Web suite. The recheck also exposed two pre-existing SFLP20/21 compiled-runtime contract failures. The parity correction removes the browser transport overclaim, clarifies ATT07 handoff-model scope, visibly renders Web-TV ATT10/ATT11 projections, fixes the SFLP20/21 runtime/TV-write semantics, and raises the terminal Web regression evidence to **290/290 passing**. See `docs/validation/ATTENTION_ROADMAP_PARITY_RECHECK.md`.


## Third adversarial compact-transport recheck

A subsequent adversarial pass found that v2 still let Desktop expose a real ATT08 mini dashboard without advertising `WIDGET_OR_BADGE` to ATT07. Under the ATT10 Travel/offline profile, `COMPACT_ONLY` filters candidates to that surface, so Desktop could become non-deliverable despite having a real compact UI. The same pass also found that native cross-device `notification.deliver` carried the occurrence but not the selected ATT07 route, allowing a remote compact owner to be rendered as an ordinary notification. This revision fixes both semantics: Desktop advertises compact capability locally and in shared/Web topology; native SFLP delivery carries the canonical ATT07 route; Android/Desktop receivers honor compact/silent delivery; and Android does not select alarm/notification surfaces when notification permission is unavailable. Regression tests and the terminal verifier cover these exact failure modes. See `docs/validation/ATTENTION_ROADMAP_COMPACT_TRANSPORT_RECHECK.md`.
