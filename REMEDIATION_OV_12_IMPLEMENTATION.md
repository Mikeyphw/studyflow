# Remediation OV12 implementation

Overlay: **OV12 — SFLP live session, projection, material, share & backup RPC semantics**

Built on successful OV11 state (`82d4f41f`).

## Delivered

- Added verified live-session mutation through `StudyFlowLiveSessionFencingPolicy` and `applyVerified`.
- Bound projection frames to accepted offer source/workspace/content hash on native and Web.
- Replaced preview-only material completion with decoded chunk assembly and chunk/aggregate SHA-256 validation.
- Required complete payload bytes for share/import write intents and rejected spoofed source/inner target substitution.
- Added account handoff and backup restore inner-target guards.
- Moved Web-TV remote input to core-equivalent lease/token/sequence control admission.
- Added capability requirements for advanced routed RPC families at the authenticated peer-server boundary.
- Added native and Web OV12 regression coverage plus an integrated Devtool validation task.

## Validation

`verifyOv12SflpAdvancedRpcSemantics` owns apply-time validation. The validation hook contains no Gradle daemon stop. It is designed to be invoked by Gradle during Devtool apply and to reuse any existing daemon.
