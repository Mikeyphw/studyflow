# ATT01–ATT12 roadmap parity recheck

This is the independent recheck performed after the first post-seal artifact. It treats roadmap parity as **behavior + production reachability + truthful capability claims**, not as the presence of classes or verifier tokens.

## Result

The ATT01–ATT12 literal handoff scope is delivered after the parity corrections in this revision, with the following intentional boundaries stated explicitly:

- Android and Desktop can send/receive targeted ATT07 notification deliveries over authenticated native SFLP peer RPC.
- Web/Web TV have the shared routing/topology model and targeted notification **ingest** runtime, but the browser WebSocket adapter in this repository is not wired as a native `notification.deliver` peer-RPC sender. The capability matrix no longer calls that `Send/receive`.
- `StudyFlowAttentionHandoffRequest` is the canonical ATT07 handoff **request model**. ATT07 does not invent a second transport. Activity/state transfer belongs to the generic SFLP handoff/transport path; `move_to_phone` on TV/Web remains a bounded continuation action, not proof of an Attention-specific network dispatcher.
- Native Android TV intentionally evaluates the local `Minimal/TV` focus policy. It does not pretend another device's editable focus configuration is synchronized. Web TV projects its browser Attention profile/outcomes.
- TV clients remain foreground-only. Normal Web remains closed-browser-capability-gated by real Push. Desktop remains process-owned.

## Platform parity by roadmap item

| Item | Android | Desktop | Web/PWA | Android TV | Web TV | Parity result |
| --- | --- | --- | --- | --- | --- | --- |
| ATT01 | canonical ledger/domain | canonical ledger/domain | canonical browser ledger/domain | canonical ledger | canonical browser ledger | PASS |
| ATT02 | native alarms/notifications/actions | N/A | N/A | N/A | N/A | PASS |
| ATT03 | widgets + compact state | N/A | N/A | N/A | N/A | PASS |
| ATT04 | N/A | persistent runtime/tray | N/A | N/A | N/A | PASS |
| ATT05 | N/A | N/A | foreground scheduler + notification/PWA/Push foundation | N/A | N/A | PASS |
| ATT06 | companion fallback | companion fallback | notifier fallback | live D-pad foreground banner + SFLP ingress | live foreground browser banner | PASS |
| ATT07 | real topology + native RPC send/receive | real topology + native RPC send/receive | real topology + targeted ingest; no direct browser notification-RPC sender | foreground-only target/receive | foreground-only projection/ingest | PASS with truthful transport boundary |
| ATT08 | phone-owned compact + QS tile/privacy projection | tray mini projection | compact center | foreground compact overlay | compact foreground projection | PASS |
| ATT09 | FileProvider `.ics` share | saved `.ics` | download/copy `.ics` | canonical-time preview | preview/continue elsewhere | PASS |
| ATT10 | editable policy + delivery effects | editable policy + delivery effects | editable policy + delivery effects | read-only local `Minimal/TV` projection | visible read-only browser profile/outcome | PASS |
| ATT11 | analytics/export/route audit | analytics/export/route audit | analytics/export/route audit | read-only reliability | visible read-only reliability | PASS |
| ATT12 | terminal native/shared gate | terminal native/shared gate | complete Web regression + release parity | native unit gate | Web regression/parity | PASS subject to native Gradle execution on target checkout |

## Recheck discoveries corrected here

1. The previous matrix incorrectly labelled Web SFLP notification envelope handling `Send/receive`; the implementation has targeted ingress/runtime handling but no browser notification-RPC sender.
2. The previous triple-check report said `283 tests passing`. The complete Web suite contains 290 tests. Two pre-existing SFLP20/21 runtime-contract failures were exposed by the independent run; this revision closes them by retaining the share/import and conflict-review wire vocabulary in generated runtime and enforcing TV read-only write rejection.
3. Web TV already projected ATT10/ATT11 values through the bridge/DOM dataset, but the foreground UI did not visibly present them. The banner now renders read-only focus profile/outcome and reliability evidence.
4. The ATT07 documentation called the handoff model a “command,” which implied transport dispatch that is not implemented by ATT07. It now states the actual contract boundary.

## Validation evidence

The recheck runs:

- every ATT01–ATT12 source verifier plus the strengthened post-seal verifier;
- every SFLP01–SFLP24 verifier and WEB20 semantic vectors;
- TypeScript/runtime parity for all 47 generated Web runtime files;
- the **complete 290-test Web suite**;
- Web release validation and release integrity (62 dist files / 58 cached-shell files);
- clean-overlay reconstruction against the untouched supplied repository with source/payload hash checks and exact changed-file inventory.

A fresh JVM/Android/Desktop/Android-TV Gradle execution still requires the target checkout because the audit sandbox cannot download or resolve the uncached Gradle 9.7.1 wrapper distribution. `verifyAttentionRoadmapPostsealCompletion` keeps the native ATT12 dependency chain and now also depends on the complete Web regression task.
