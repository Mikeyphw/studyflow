# Remediation OV09 — SFLP native/Web interoperability transport

Overlay 9 of 30 closes S4-03, S4-04, S4-05, S4-06, S4-19, DS2-53 and DS2-55.

## Delivered

- RFC 6455 `ws://` SFLP bridge with the `studyflow.sflp.v1` subprotocol and native runtime registration.
- Canonical `studyflow://pair?data=...` QR pairing for browsers, using the host-issued trust secret and native reconnect HMAC transcript.
- Web journal fields/frontiers aligned with the native operation schema and distinct persistent Web/Web-TV identities.
- Authenticated production dispatch for SFLP14–SFLP23 handoff, live-session, presentation, control/input, notification/media, share/import/conflict, account/backup and validation message families.
- Android, Android TV and Desktop advertise browser-capable WebSocket endpoints alongside native TCP endpoints.
- Real WebSocket native test plus browser wire-contract and five-client parity tests.

## Boundary

OV09 establishes interoperability. TLS/WSS, secret-at-rest hardening, fail-closed browser randomness, expiry/target/capability enforcement and related trust hardening remain explicitly owned by OV10.

## Validation

`verifyOv09SflpNativeWebInterop` is the Devtool validation task. Its source validator performs structural and Web runtime tests and intentionally does not invoke or stop Gradle daemons; Gradle owns compilation/tests around it.
