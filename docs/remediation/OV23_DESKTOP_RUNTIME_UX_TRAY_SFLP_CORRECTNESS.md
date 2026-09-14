# Remediation OV23 — Desktop runtime, UX, tray & SFLP correctness

Overlay 23 of 30 continues the Desktop wave after OV22 sealed persistence, atomic restore, and file safety.

## Baseline

- Roadmap position: Overlay 23 of 30.
- Previous overlay: OV22 — Desktop persistence, atomicity, restore & file safety.
- User-applied state: cumulative StudyFlow remediation through OV22.

## Findings closed

- `S9-10` — Desktop SFLP pairing could advertise a loopback-only endpoint that another device could never use.
- `S9-11` — Manual/QR Desktop peer endpoint ingestion accepted self-loopback and wildcard addresses as if they were reachable peers.
- `S9-12` — Desktop sync action copy claimed success by peer count and hid retryable/terminal failures.
- `S9-13` — Desktop Settings lacked link-health and reachable-peer truth, making offline peers look equivalent to working peers.
- `S9-14` — Desktop pairing expiry was shown as raw epoch milliseconds rather than local user time.
- `S9-15` — AWT tray actions could let callback exceptions escape the native event path instead of surfacing a bounded StudyFlow failure.
- `S10-01` — Desktop reminder Settings time labels derived from raw epoch minutes instead of formatting the actual instant in the local timezone.
- `DS2-46` — Desktop restore copy still claimed restart was required even after OV22 added immediate in-memory reload.

## Implementation

### Desktop SFLP endpoint truth

`DesktopStudyFlowLinkUxPolicy` owns Desktop-facing StudyFlow Link policy. It filters shareable pairing hints to transport schemes that another device can use and removes loopback/wildcard hosts such as `localhost`, `127.*`, `0.0.0.0`, `::`, and `::1`. If the desktop only has loopback addresses, pairing offer creation fails with an explicit no-shareable-endpoint message instead of emitting a misleading QR payload.

Manual endpoints and QR endpoint hints are parsed through the same policy before being remembered. This keeps Desktop peer state from filling with self-loopback addresses that pass parsing but cannot connect to another physical client.

### Retry-aware sync and link health

The Desktop sync button now reports complete, partial, retryable, and terminal outcomes through `DesktopStudyFlowLinkUxPolicy.syncSummary`. `DesktopStudyFlowLinkState` carries `healthMessage`, `capabilityMessage`, and per-peer `reachable` evidence so Settings can explain why sync is unavailable without pretending a remembered peer is currently reachable.

### Tray and runtime UX correctness

The AWT tray action bridge now wraps callback invocation in `runCatching` and reports a bounded “StudyFlow action failed” tray message when a handler throws. This prevents native menu handlers from escaping the UI event path while still making the failure visible.

Desktop reminder labels now format `Instant.ofEpochMilli(epochMillis)` with the system timezone rather than deriving `HH:mm` from raw epoch minutes. Restore copy in Settings now matches OV22 behavior: rollback-protected restore reloads Desktop memory immediately and does not require restart.

## Validation

The overlay adds `verifyOv23DesktopRuntimeUxSflpCorrectness`. The artifact validation hook runs that Gradle task during Devtool apply and does not stop Gradle daemons.

The source verifier checks endpoint filtering, loopback-fallback removal, manual/QR endpoint validation, retry-aware sync summaries, link-health Settings copy, readable pairing expiry, tray callback isolation, local-time reminder labels, restore copy accuracy, OV23 unit-test coverage, Gradle task wiring, and validation hook behavior.
