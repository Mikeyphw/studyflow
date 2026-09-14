# OV12 — SFLP live session, projection, material, share & backup RPC semantics

## Scope

Overlay 12 hardens the advanced SFLP RPC families introduced across SFLP14–SFLP23 so they are not just routable, but semantically enforced at native and Web runtime boundaries.

Owned findings closed:

- `S4-11`: material streaming cannot mark `COMPLETE` unless all bytes are ordered, decoded and SHA-256 verified.
- `S4-16`: live-session fencing token and active executor identity are enforced before state mutation.
- `S4-20`: share/import write delivery carries full content bytes, not only preview metadata.
- `DS2-20`: Web live-session continuity rejects wrong session, workspace, activity, fencing token and executor source.
- `DS2-21`: Web projection frames are bound to the accepted offer's source, workspace and content hash.
- `DS2-22`: Web-TV remote input no longer accepts target-device-only commands.
- `DS2-23`: Web remote control uses the same lease, sequence, target and token reducer contract as core.
- `DS2-24`: final material chunks cannot complete an out-of-order or incomplete stream.
- `DS2-25`: Web share/import rejects expired, wrong-source, wrong-target, capability-ineligible and malformed full-content offers.
- `DS2-26`: account/backup handoff and restore reject inner target substitutions.

## Native behavior

`StudyFlowLiveSessionReducer.applyVerified` now validates the event fence before mutation. The `StudyFlowLiveSessionContinuityController` emits fenced events and routes them through the verified path.

`StudyFlowPresentationProjectionReceiver` persists the accepted offer and validates each frame's source device, workspace and content hash before sequence advancement.

`StudyFlowMaterialStreamReducer` assembles real decoded chunk bytes, rejects invalid Base64 and SHA mismatches, enforces contiguous chunk indexes and offsets, forbids short non-final chunks, and compares the aggregate stream bytes to the offer content hash before returning `COMPLETE`.

`StudyFlowShareImportOffer` now supports `contentBytesBase64`; write intents require a complete payload with a matching SHA-256 hash. The reducer also rejects source spoofing and inner target substitution before admitting the import.

`StudyFlowAccountHandoffReducer` and `StudyFlowBackupRestoreReducer` reject handoff/restore envelopes whose inner target differs from the outer target/local device. Backup bundle manifests carry an optional target device to bind restore plans.

`StudyFlowRoutedRpcContracts` now declares sender capability requirements for authority-bearing advanced routed RPC families, and `StudyFlowAdvancedRpcSemanticHandlers` records the server-handled advanced families audited by this overlay.

## Web behavior

The browser runtime mirrors the native constraints. Live session, projection, Web-TV input, material streaming, share/import and account/backup runtime paths all reject the audited malformed or spoofed cases. `StudyFlowWebHashing` exposes the runtime SHA-256/Base64 primitives used by Web material and share/import validation tests.

## Validation

The overlay registers `verifyOv12SflpAdvancedRpcSemantics`. The task depends on native compilation/tests for the link, TV-control, TV-link, Android, Android TV and Desktop surfaces, then runs `scripts/validate_ov12_sflp_advanced_rpc_semantics.sh`.

The validation script intentionally does not stop Gradle daemons. It runs source evidence checks, TypeScript runtime parity, OV09/OV10/OV11 compatibility runners, the OV12 advanced RPC runner and focused Web unit tests.
