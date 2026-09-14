# OV21 — Unified TV control, pairing, relay, cast & attention plane

Overlay 21 of 30 closes the TV-plane split that remained after the native-TV and attention remediation waves. Before OV21, authenticated local TCP requests, Web relay controller requests, live cast delivery, domain-action acknowledgements and TV attention ingress each had separate admission semantics. OV21 introduces one signed, transport-neutral TV-plane envelope.

## Closed findings

- `S9-01` — Local TCP and browser relay control paths now share a canonical TV-plane contract.
- `S9-02` — Authenticated wire requests carry a signed `StudyFlowTvPlaneEnvelope`.
- `S9-03` — Receiver/session/operation mismatches fail closed before TV state changes.
- `S9-04` — Payload tampering after envelope creation is detected by SHA-256 body hash.
- `S9-05` — Presentation delivery and STOP_CAST carry an explicit cast scope.
- `S9-06` — Domain-action acknowledgement is bound to the acknowledged canonical action id.
- `S9-07` — TV attention ingress now exposes canonical-plane evidence instead of living as a parallel policy island.
- `S9-08` — Web relay rejects missing/mismatched plane envelopes and keeps bearer/controller tokens forbidden.
- `S9-09` — Browser remote attaches the same plane envelope before signing requests.

## Validation

`verifyOv21UnifiedTvControlPlane` runs the source-evidence verifier and the Web relay plane runner. The artifact validation hook reuses Gradle daemons and does not stop them.
