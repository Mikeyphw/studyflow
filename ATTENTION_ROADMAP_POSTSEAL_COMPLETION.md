# StudyFlow Attention roadmap — post-ATT12 completion hotfix

This hotfix closes the production-reachability gaps found by re-auditing ATT01–ATT12 against the original Attention handoff roadmap. It does not add a new roadmap phase; it makes the existing promises true in the product paths that ATT12 previously failed to inspect.

## What was incomplete after ATT12

- **ATT06:** Android TV rendered an Attention runtime but the live SFLP peer never delivered `notification.deliver` messages into `StudyFlowTvAttentionRuntime.ingest`.
- **ATT07:** Android, Desktop, and Web could compute cross-device routes, but their normal routing candidate sets were effectively local-only. Android/Desktop also used synthetic Attention IDs instead of canonical SFLP device identities.
- **ATT08:** Android/Desktop/Android-TV compact companion bridges existed but were not consumed by production surfaces.
- **ATT09:** Android/Desktop/Android-TV calendar bridges existed but only Web exposed a real user flow.
- **ATT10/ATT11:** the native-TV focus/reliability projections existed as helpers but were not surfaced; cross-device reliability inherited ATT07's incomplete topology.
- **ATT12:** its verifier checked model/token existence and explicit target guards, but did not prove the missing ingress, peer candidate topology, or bridge call sites.

## Completion delivered

### ATT06 / SFLP notification ingress

The generic SFLP peer RPC server now accepts authenticated `notification.deliver` requests, validates source/target/workspace identity, invokes a registered notification handler, and returns a typed notification acknowledgement. Android, Desktop, and Android TV register live handlers: native Android surfaces an accepted delivery through its real notification channel, Desktop uses its process-owned native notification port, and Android TV feeds the live ATT06 foreground runtime. A real-TCP peer integration test exercises the authenticated transport. Delivery sequences are process-monotonic even when two reminders are emitted in the same millisecond, so ATT01 stale-sequence protection cannot reject a valid second delivery.

### ATT07 canonical cross-device routing

`StudyFlowAttentionWorkspace` and `StudyFlowAttentionTopologyProjector` unify the plan workspace, canonical device identity, trusted peers, and presence into delivery candidates. Android and Desktop use their real SFLP identities, include reachable trusted peers, and dispatch selected remote owner/mirror targets over authenticated SFLP notification transport. Web routing now evaluates the trusted/presence topology rather than a one-element local list. TV/Web remain foreground-only candidates unless presence proves they are active.

### ATT08 compact companion reachability

Android projects compact cards from the actual ATT07 route, persists the phone-owned projection for a real Quick Settings `TileService`, keeps notification copy private/redacted on public lock-screen surfaces, and permits OS companion notification bridging instead of forcing `localOnly`. Desktop projects the compact card into the tray mini-dashboard and advertises that real surface to ATT07 as `WIDGET_OR_BADGE`. Android TV projects the active foreground delivery into its compact remote overlay. Web keeps its existing compact surface. `COMPACT_ONLY` remains genuinely compact-only: it advances canonical/widget state without fabricating a system notification. Native Android/Desktop remote envelopes carry the selected ATT07 route, so compact and silent semantics survive cross-device transport. Android also falls back to compact widget/tile delivery when notification permission makes native notification surfaces unusable.

### ATT09 calendar export reachability

Android writes the canonical exporter result to a real cache-backed `.ics` file, shares it as `text/calendar` through `FileProvider`, and grants only temporary read access; Desktop exposes an `.ics` save flow through the native file dialog and writes the exporter result; Android TV builds its read-only preview from the real projected schedule timestamps/durations and skips unscheduled rows instead of inventing times; Web retains its download/copy flow. None of these paths silently mutate an external calendar provider.

### ATT10 / ATT11 TV and reliability reachability

Android TV now consumes the ATT10 read-only focus projection and ATT11 reliability projection in Settings. Web ATT11 reliability uses the same peer topology used by ATT07 route decisions rather than a local-only capability list.

### ATT12 validation hardening

The ATT12 verifier now covers canonical topology, authenticated notification transport, native Android/Desktop/TV ingress, bounded-mirror continuation after remote-owner success, genuine compact-only behavior, ATT08 production call sites, real `.ics` sharing, truthful TV calendar timestamps, ATT09 production call sites, TV ATT10/ATT11 projection reachability, and Web topology-backed reliability. `verifyAttentionRoadmapPostsealCompletion` additionally rejects the exact disconnected/local-only patterns that escaped the earlier seal, including Desktop compact-capability omission and remote compact-route loss.

## Final validation gate

Run `verifyAttentionRoadmapPostsealCompletion`. It depends on the complete ATT12 seal, which already depends on ATT11 through ATT01, SFLP18/19, WEB20 semantic vectors, shared-link tests, Android release unit tests, Desktop tests, Android-TV tests, and Web parity/release checks.

Platform truth remains unchanged: Android TV/Web TV are foreground-only; Desktop reminders require the process to be running; normal Web closed-browser delivery still requires real Push infrastructure; compact companions never become independent owners.
## Independent parity recheck

A later independent audit rechecked this completion against the literal handoff and the five-client capability matrix. It corrected one remaining documentation overclaim: Web/Web TV can evaluate trusted/presence routing and ingest targeted Attention envelopes, but this repository does not wire the browser WebSocket adapter as a native `notification.deliver` peer-RPC sender. It also clarifies that `StudyFlowAttentionHandoffRequest` is the canonical ATT07 request model rather than an Attention-specific transport.

The recheck also made Web-TV ATT10/ATT11 projections visibly user-facing and exposed two pre-existing SFLP20/21 compiled-runtime contract failures. Those runtime semantics are now repaired, TV write/import/conflict resolution remains rejected, and the complete Web suite passes **290/290**. `verifyAttentionRoadmapPostsealCompletion` now includes the full Web regression task in addition to the ATT12/native/shared dependency chain. See `docs/validation/ATTENTION_ROADMAP_PARITY_RECHECK.md`.



## Third compact-transport recheck

The third adversarial pass specifically validates ATT08/ATT10 compact ownership and native cross-device surface preservation. See `docs/validation/ATTENTION_ROADMAP_COMPACT_TRANSPORT_RECHECK.md`.
