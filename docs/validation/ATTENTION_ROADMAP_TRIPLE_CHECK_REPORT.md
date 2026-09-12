# ATT01–ATT12 Attention roadmap triple-check report

This report is the terminal audit for the post-ATT12 completion work. It compares the literal handoff roadmap promises with production call paths, roadmap-parity truth across five clients, and validation that rejects the disconnected implementations found after the original ATT12 seal.

## Check 1 — literal roadmap promise reconciliation

| Item | Literal promise areas checked | Result |
| --- | --- | --- |
| ATT01 | shared Attention domain, durable ledger/lifecycle, occurrence identity, SFLP foundation, cross-device-safe regression | PASS |
| ATT02 | Android planner/runtime, categories, native notification/actions, foreground/background truth | PASS |
| ATT03 | Android widget projection, quick actions, ATT01/ATT02 regression | PASS |
| ATT04 | Desktop persistent preferences/runtime/tray/settings and truthful unsupported fallback | PASS |
| ATT05 | Web permission/runtime/scheduler, SW lifecycle, center, controls, optional Push, badge, ledger convergence, generated parity | PASS |
| ATT06 | native-TV + Web-TV foreground attention, D-pad actions, ATT01 acknowledgement, foreground-only truth, diagnostics, TV drain | PASS |
| ATT07 | single owner, bounded mirrors, five-client capability scoring, ledger suppression, explicit targets, foreground-first/phone-desktop routing, TV truth, canonical handoff request model/adapters | PASS with transport boundary documented |
| ATT08 | parent-owned compact model, privacy-safe copy, bounded actions, Android/Desktop/TV/Web consumers, Web compact/drain surface | PASS |
| ATT09 | shared export, ICS `VEVENT` + `VALARM`, full/private/busy modes, ATT01 projection, Android/Desktop/TV/Web product paths, Web parity | PASS |
| ATT10 | timezone-safe/overnight quiet hours, focus profiles, per-category escalation, explicit outcomes, ATT07/ATT01 integration, Android/Desktop/Web controls, TV-safe projections | PASS |
| ATT11 | unified evidence, platform reliability, outcome analytics, debug/export, cross-device route audit | PASS |
| ATT12 | five-client integration, truthful docs/matrix, explicit validation list, promise audit, release seal/final gate | PASS after post-seal + parity hardening |

Roadmap parity is semantic rather than API symmetry. Android/Desktop have native SFLP notification peer-RPC send/receive; Web/Web TV expose the routing/topology and targeted-ingest contracts but do **not** claim a direct browser `notification.deliver` peer-RPC sender. The ATT07 `StudyFlowAttentionHandoffRequest` is the canonical request model, while generic SFLP owns actual state-transfer transport. TV/Web-TV remain foreground-only, normal Web does not claim closed-browser delivery without real Push, Desktop remains process-owned, and compact companions are not independent owners.

## Check 2 — production reachability and semantic edge cases

The audit follows production callers rather than accepting class or token existence. The final implementation proves these previously missed edges:

- Android/Desktop/Web build ATT07 candidates from canonical SFLP identity plus trusted/reachable peers and presence instead of a local-only list.
- Android and Desktop send selected remote owner/mirror targets through authenticated `notification.deliver` SFLP peer RPC and register inbound handlers.
- Android TV registers an inbound SFLP handler and feeds accepted deliveries into `StudyFlowTvAttentionRuntime.ingest`.
- Web targeted notification envelopes enter the same ATT01 browser ledger/runtime; the capability matrix explicitly distinguishes this from an unwired direct browser peer-RPC sender.
- Remote-owner success does not suppress a selected local mirror.
- ATT01 stale-sequence protection cannot reject a second valid same-millisecond remote delivery because wire sequences are process-monotonic.
- Android `COMPACT_ONLY` remains compact-only and does not post a system notification.
- ATT08 Android, Desktop and native-TV bridges have actual product consumers; Web retains its compact surface.
- Android ATT09 writes/shares a cache-backed `.ics` stream through `FileProvider`; Desktop saves `.ics`; native TV previews canonical schedule timestamps/durations and skips unscheduled rows; Web retains download/copy.
- Native TV consumes ATT10 focus and ATT11 reliability projections. Native TV intentionally uses the local `Minimal/TV` policy; Web TV visibly renders its read-only browser focus profile/outcome and reliability state.
- Android legacy reminder migration uses the same canonical plan-to-workspace helper as live routing.
- SFLP20/21 Web runtime now retains the share/import/conflict wire vocabulary at runtime and enforces TV read-only rejection instead of allowing write resolution on TV clients.

## Check 3 — validation, parity and artifact reproducibility

The completed tree passes the available source/protocol/runtime gates:

- SFLP01 through SFLP24 source verifiers.
- WEB20 shared semantic golden vectors (40 vectors).
- ATT01 through ATT12 source verifiers.
- `verify_attention_roadmap_postseal_completion.py`, which rejects the local-only/orphan-ingress/orphan-bridge/compact-only/calendar-truth and roadmap-parity false claims discovered by the audits.
- TypeScript/runtime parity: 47 generated Web runtime files.
- **Complete Web suite: 290 tests passing.**
- Web release validation passes.
- Web release integrity: 62 dist files and 58 cached-shell files.

The overlay is smoke-applied to an untouched copy of the supplied repository. Its manifest source hashes, payload hashes/sizes, safe archive paths and exact changed-file coverage are checked before release; the same source/protocol/Web gates are rerun from that smoke tree.

See `docs/validation/ATTENTION_ROADMAP_PARITY_RECHECK.md` for the explicit five-client parity matrix and transport-boundary findings.

### Native Gradle execution boundary

This audit environment cannot download the Gradle 9.7.1 wrapper distribution and does not have that distribution cached, so it cannot honestly claim a fresh local JVM/Android/Desktop/Android-TV Gradle execution. The terminal Devtool task is `verifyAttentionRoadmapPostsealCompletion`: it depends on the ATT12 seal and its native/shared/Web dependencies and now also runs the complete Web regression suite, so the target StudyFlow checkout performs the native Gradle validation and rolls back the overlay if that final gate fails.
