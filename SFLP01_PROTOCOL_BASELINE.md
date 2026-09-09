# SFLP01 protocol baseline

SFLP01 establishes the first platform-neutral StudyFlow Link Protocol contract.

## Delivered

- Added the pure Kotlin/JVM `:core:link` module with no Android dependency.
- Added protocol version/range negotiation with fail-closed major compatibility.
- Added stable workspace, device, entity, operation, message, and revision wire identities.
- Added the Android/Desktop/Web/Android-TV/Web-TV peer descriptor.
- Added the v1 capability vocabulary used by the full-connectivity roadmap.
- Added request/response/event/error envelope semantics and structured link errors.
- Added deterministic Kotlin JSON codec behavior that tolerates additive unknown fields within a major.
- Added a language-neutral JSON Schema and request/response/event/error golden vectors.
- Added JVM unit tests that enforce negotiation, envelope invariants, capability stability, and golden
  round-trips.

## Explicitly deferred

SFLP01 does not claim device discovery, trust/pairing, network transport, operation replication, conflict
resolution, or cross-client synchronization. SFLP02 starts the domain operation journal; SFLP04 and
SFLP05 later add trust and transport.

## Validation

The focused Gradle gate is:

```text
verifySflp01ProtocolBaseline
```

It depends on `:core:link:test` and runs `scripts/verify_sflp01_protocol_baseline.py`.
